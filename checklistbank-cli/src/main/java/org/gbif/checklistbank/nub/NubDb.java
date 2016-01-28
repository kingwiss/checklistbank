package org.gbif.checklistbank.nub;

import org.gbif.api.vocabulary.Kingdom;
import org.gbif.api.vocabulary.NameUsageIssue;
import org.gbif.api.vocabulary.Origin;
import org.gbif.api.vocabulary.Rank;
import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.checklistbank.authorship.AuthorComparator;
import org.gbif.checklistbank.model.Equality;
import org.gbif.checklistbank.neo.Labels;
import org.gbif.checklistbank.neo.NeoProperties;
import org.gbif.checklistbank.neo.RelType;
import org.gbif.checklistbank.neo.UsageDao;
import org.gbif.checklistbank.neo.traverse.Traversals;
import org.gbif.checklistbank.nub.model.NubUsage;
import org.gbif.checklistbank.nub.model.NubUsageMatch;
import org.gbif.checklistbank.nub.model.SrcUsage;
import org.gbif.common.parsers.KingdomParser;
import org.gbif.common.parsers.core.ParseResult;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.helpers.collection.IteratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper around the dao that etends the dao with nub build specific common operations.
 */
public class NubDb {

  private static final Logger LOG = LoggerFactory.getLogger(NubDb.class);
  private final AuthorComparator authComp;
  protected final UsageDao dao;
  private final KingdomParser kingdomParser = KingdomParser.getInstance();
  private final Map<Kingdom, NubUsage> kingdoms = Maps.newHashMap();

  private NubDb(UsageDao dao, AuthorComparator authorComparator, boolean initialize) {
    this.dao = dao;
    this.authComp = authorComparator;
    // create indices?
    if (initialize) {
      try (Transaction tx = dao.beginTx()) {
        Schema schema = dao.getNeo().schema();
        schema.indexFor(Labels.TAXON).on(NeoProperties.CANONICAL_NAME).create();
        tx.success();
      }
    }
  }

  public static NubDb create(UsageDao dao, AuthorComparator authorComparator) {
    return new NubDb(dao, authorComparator, true);
  }

  /**
   * Opens an existing db without creating a new db schema.
   */
  public static NubDb open(UsageDao dao, AuthorComparator authorComparator) {
    return new NubDb(dao, authorComparator, false);
  }

  public Transaction beginTx() {
    return dao.beginTx();
  }

  /**
   * @return the parent (or accepted) nub usage for a given node. Will be null for kingdom root nodes.
   */
  public NubUsage getParent(NubUsage child) throws NotFoundException {
    return child == null ? null : dao.readNub(getParent(child.node));
  }

  public NubUsage getKingdom(Kingdom kingdom) {
    return kingdoms.get(kingdom);
  }

  /**
   * @return the parent (or accepted) nub usage for a given node. Will be null for kingdom root nodes.
   */
  public Node getParent(Node child) {
    if (child != null) {
      Relationship rel;
      if (child.hasLabel(Labels.SYNONYM)) {
        rel = child.getSingleRelationship(RelType.SYNONYM_OF, Direction.OUTGOING);
      } else {
        rel = child.getSingleRelationship(RelType.PARENT_OF, Direction.INCOMING);
      }
      if (rel != null) {
        return rel.getOtherNode(child);
      }
    }
    return null;
  }

  /**
   * @return the neo node with the given node id or throw NotFoundException if it cannot be found!
   */
  public Node getNode(long id) throws NotFoundException {
    return dao.getNeo().getNodeById(id);
  }

  /**
   * Returns the matching accepted nub usage for that canonical name at the given rank.
   */
  public NubUsageMatch findAcceptedNubUsage(Kingdom kingdom, String canonical, Rank rank) {
    List<NubUsage> usages = Lists.newArrayList();
    for (Node n : IteratorUtil.loop(dao.getNeo().findNodes(Labels.TAXON, NeoProperties.CANONICAL_NAME, canonical))) {
      NubUsage rn = dao.readNub(n);
      if (kingdom == rn.kingdom && rank == rn.rank && rn.status.isAccepted()) {
        usages.add(rn);
      }
    }
    // remove doubtful ones
    if (usages.size() > 1) {
      Iterator<NubUsage> iter = usages.iterator();
      while (iter.hasNext()) {
        if (iter.next().status == TaxonomicStatus.DOUBTFUL) {
          iter.remove();
        }
      }
    }

    if (usages.isEmpty()) {
      return NubUsageMatch.empty();
    } else if (usages.size() == 1) {
      return NubUsageMatch.match(usages.get(0));
    } else {
      LOG.error("{} homonyms encountered for {} {}", usages.size(), rank, canonical);
      throw new IllegalStateException("accepted homonym encountered for " + kingdom + " kingdom: " + rank + " " + canonical);
    }
  }

  /**
   * Tries to find an already existing nub usage for the given source usage.
   *
   * @return the existing usage or null if it does not exist yet.
   */
  public NubUsageMatch findNubUsage(UUID currSource, SrcUsage u, Kingdom uKingdom, NubUsage currNubParent) throws IgnoreSourceUsageException {
    final boolean qualifiedName = u.parsedName.hasAuthorship();
    List<NubUsage> checked = Lists.newArrayList();
    int canonMatches = 0;
    final String name = dao.canonicalOrScientificName(u.parsedName, false);
    for (Node n : IteratorUtil.loop(dao.getNeo().findNodes(Labels.TAXON, NeoProperties.CANONICAL_NAME, name))) {
      NubUsage rn = dao.readNub(n);
      if (matchesNub(u, uKingdom, rn, currNubParent)) {
        checked.add(rn);
        if (!rn.parsedName.hasAuthorship()) {
          canonMatches++;
        }
      }
    }

    if (checked.isEmpty()) {
      // try harder to match to kingdoms by name alone
      if (u.rank != null && u.rank.higherThan(Rank.PHYLUM)) {
        ParseResult<Kingdom> kResult = kingdomParser.parse(u.scientificName);
        if (kResult.isSuccessful()) {
          return NubUsageMatch.snap(kingdoms.get(kResult.getPayload()));
        }
      }
      return NubUsageMatch.empty();
    }

    // first try exact single match with authorship
    if (qualifiedName) {
      NubUsage match = null;
      for (NubUsage nu : checked) {
        if (u.parsedName.canonicalNameComplete().equals(nu.parsedName.canonicalNameComplete())) {
          if (match != null) {
            LOG.warn("Exact homonym encountered for {}", u.scientificName);
            match = null;
            break;
          }
          match = nu;
        }
      }
      if (match != null) {
        return NubUsageMatch.match(match);
      }
    }

    // avoid the case when an accepted name without author is being matched against synonym names with authors from the same source
    if (u.status.isAccepted() && !qualifiedName && currSource.equals(qualifiedSynonymsFromSingleSource(checked))) {
      LOG.debug("{} canonical homonyms encountered for {}, but all from the same source", checked.size(), u.scientificName);
      return NubUsageMatch.empty();
    }

    if (checked.size() == 1) {
      return NubUsageMatch.match(checked.get(0));
    }

    // we have at least 2 match candidates here, maybe more
    // prefer a single match with authorship!
    if (qualifiedName && checked.size() - canonMatches == 1) {
      for (NubUsage nu : checked) {
        if (nu.parsedName.hasAuthorship()) {
          return NubUsageMatch.match(nu);
        }
      }
    }

    // all synonyms pointing to the same accepted? then it wont matter much for snapping
    Iterator<NubUsage> iter = checked.iterator();
    Node parent = getParent(iter.next().node);
    if (parent != null) {
      while (iter.hasNext()) {
        Node p2 = getParent(iter.next().node);
        if (!parent.equals(p2)) {
          parent = null;
          break;
        }
      }
      if (parent != null) {
        return NubUsageMatch.snap(checked.get(0));
      }
    }

    // try to do better authorship matching, remove canonical matches
    if (qualifiedName) {
      iter = checked.iterator();
      while (iter.hasNext()) {
        NubUsage nu = iter.next();
        Equality author = authComp.compare(u.parsedName, nu.parsedName);
        if (author != Equality.EQUAL) {
          iter.remove();
        }
      }
    }

    // finally pick the first accepted randomly
    for (NubUsage nu : checked) {
      if (nu.status.isAccepted()) {
        nu.issues.add(NameUsageIssue.HOMONYM);
        nu.addRemark("Homonym known in other sources: " + u.scientificName);
        LOG.warn("{} ambigous homonyms encountered for {} in source {}", checked.size(), u.scientificName, currSource);
        return NubUsageMatch.snap(nu);
      }
    }
    throw new IgnoreSourceUsageException("homonym " + u.scientificName, u.scientificName);
  }

  /**
   * Checks if all nubusages are synonyms, have authorship and origin from the same source
   * @return the source UUID all usages share
   */
  private UUID qualifiedSynonymsFromSingleSource(Collection<NubUsage> usages) {
    UUID source = null;
    for (NubUsage nu : usages) {
      if (!nu.parsedName.hasAuthorship() || nu.status.isAccepted()) {
        return null;
      }
      if (source == null) {
        source = nu.datasetKey;
      } else if (!source.equals(nu.datasetKey)) {
        return null;
      }
    }
    return source;
  }

  private boolean matchesNub(SrcUsage u, Kingdom uKingdom, NubUsage match, NubUsage currNubParent) {
    if (u.rank != match.rank) {
      return false;
    }
    // no homonyms above genus level
    if (u.rank.isSuprageneric()) {
      return true;
    }
    Equality author = authComp.compare(u.parsedName, match.parsedName);
    Equality kingdom = compareKingdom(uKingdom, match);
    if (author == Equality.DIFFERENT || kingdom == Equality.DIFFERENT) return false;
    switch (author) {
      case EQUAL:
        // really force a no-match in case authors match but the name is classified under a different (normalised) kingdom?
        return true;
      case UNKNOWN:
        return u.rank.isSpeciesOrBelow() || compareClassification(currNubParent, match) != Equality.DIFFERENT;
    }
    return false;
  }

  // if authors are missing require the classification to not contradict!
  private Equality compareKingdom(Kingdom uKingdom, NubUsage match) {
    if (uKingdom == null || match.kingdom == null) {
      return Equality.UNKNOWN;
    }
    return norm(uKingdom) == norm(match.kingdom) ? Equality.EQUAL : Equality.DIFFERENT;
  }

  // if authors are missing require the classification to not contradict!
  private Equality compareClassification(NubUsage currNubParent, NubUsage match) {
    if (currNubParent != null && existsInClassification(match.node, currNubParent.node)) {
      return Equality.EQUAL;
    }
    return Equality.DIFFERENT;
  }

  public long countTaxa() {
    Result res = dao.getNeo().execute("start node=node(*) match node return count(node) as cnt");
    return (long) res.columnAs("cnt").next();
  }

  /**
   * distinct only 3 larger groups of kingdoms as others are often conflated.
   */
  private Kingdom norm(Kingdom k) {
    switch (k) {
      case ANIMALIA:
      case PROTOZOA:
        return Kingdom.ANIMALIA;
      case PLANTAE:
      case FUNGI:
      case CHROMISTA:
        return Kingdom.PLANTAE;
      default:
        return Kingdom.INCERTAE_SEDIS;
    }
  }

  public NubUsage addUsage(NubUsage parent, SrcUsage src, Origin origin, UUID sourceDatasetKey, NameUsageIssue ... issues) {
    NubUsage nub = new NubUsage(src);
    nub.datasetKey = sourceDatasetKey;
    nub.origin = origin;
    if (src.key != null) {
      nub.sourceIds.add(src.key);
    }
    if (issues != null) {
      Collections.addAll(nub.issues, issues);
    }
    return addUsage(parent, nub);
  }

  /**
   * @param parent classification parent or accepted name in case the nub usage has a synonym status
   */
  public NubUsage addUsage(NubUsage parent, NubUsage nub) {
    Preconditions.checkNotNull(parent);
    return add(parent, nub);
  }

  public NubUsage addRoot(NubUsage nub) {
    return add(null, nub);
  }

  /**
   * @param parent classification parent or accepted name in case the nub usage has a synonym status
   */
  private NubUsage add(@Nullable NubUsage parent, NubUsage nub) {
    if (nub.node == null) {
      // create new neo node if none exists yet (should be the regular case)
      nub.node = dao.createTaxon();
    }
    LOG.debug("created node {} for {} {} {} {} with {}",
        nub.node.getId(),
        nub.origin == null ? "" : nub.origin.name().toLowerCase(),
        nub.status == null ? "" : nub.status.name().toLowerCase(),
        nub.parsedName == null ? "no parsed nane" : nub.parsedName.getScientificName(),
        nub.rank,
        parent == null ? "no parent" : "parent " + parent.parsedName.getScientificName()
    );

    if (parent == null) {
      nub.node.addLabel(Labels.ROOT);
    } else {
      nub.kingdom = parent.kingdom;
      if (nub.status != null && nub.status.isSynonym()) {
        createSynonymRelation(nub.node, parent.node);
      } else {
        createParentRelation(nub, parent);
      }
    }
    // flag autonyms
    if (nub.parsedName != null && nub.parsedName.isAutonym()) {
      nub.node.addLabel(Labels.AUTONYM);
    }
    // add rank specific labels so we can easily find them later
    switch (nub.rank) {
      case FAMILY:
        nub.node.addLabel(Labels.FAMILY);
        break;
      case GENUS:
        nub.node.addLabel(Labels.GENUS);
        break;
      case SPECIES:
        nub.node.addLabel(Labels.SPECIES);
        break;
      case SUBSPECIES:
      case VARIETY:
      case SUBVARIETY:
      case FORM:
      case SUBFORM:
        nub.node.addLabel(Labels.INFRASPECIES);
        break;
    }
    return store(nub);
  }

  /**
   * Creates a new parent relation from parent to child node and removes any previously existing parent relations of
   * the child node.
   * Parent child relations with genus or species epithet not matching up are flagged with NameUsageIssue.NAME_PARENT_MISMATCH
   */
  public boolean createParentRelation(NubUsage n, NubUsage parent) {
    return createParentRelation(n.node, parent.node, n.rank, parent.rank);
  }

  private boolean createParentRelation(Node n, Node parent, Rank nRank, Rank parentRank) {
    if (n.equals(parent)) {
      LOG.warn("Avoid setting set parent rel to oneself on {} {}", n, NeoProperties.getScientificName(n));
      return false;
    }
    if (nRank.higherThan(parentRank)) {
      LOG.warn("Avoid setting inverse parent rel from {} {} to {} {}", parent, NeoProperties.getScientificName(parent), n, NeoProperties.getScientificName(n));
      return false;
    }
    setSingleToRelationship(parent, n, RelType.PARENT_OF);
    return true;
  }

  /**
   * Create a new relationship of given type from to nodes and make sure only a single relation of that kind exists at the "to" node.
   * If more exist delete them silently.
   */
  private Relationship setSingleToRelationship(Node from, Node to, RelType reltype) {
    deleteRelations(to, reltype, Direction.INCOMING);
    return from.createRelationshipTo(to, reltype);
  }

  public Relationship setSingleFromRelationship(Node from, Node to, RelType reltype) {
    deleteRelations(from, reltype, Direction.OUTGOING);
    return from.createRelationshipTo(to, reltype);
  }

  public Relationship createSynonymRelation(Node synonym, Node accepted) {
    deleteRelations(synonym, RelType.PARENT_OF, Direction.INCOMING);
    deleteRelations(synonym, RelType.SYNONYM_OF, Direction.OUTGOING);
    // delete pro parte rel if it also links to the same accepted
    for (Relationship rel : synonym.getRelationships(RelType.PROPARTE_SYNONYM_OF, Direction.OUTGOING)) {
      if (rel.getOtherNode(synonym).equals(accepted)) {
        rel.delete();
      }
    }
    synonym.addLabel(Labels.SYNONYM);
    synonym.removeLabel(Labels.ROOT);
    return synonym.createRelationshipTo(accepted, RelType.SYNONYM_OF);
  }

  public boolean deleteRelations(Node n, RelType type, Direction direction) {
    boolean deleted = false;
    for (Relationship rel : n.getRelationships(type, direction)) {
      rel.delete();
      deleted = true;
    }
    return deleted;
  }

  /**
   * Converts the given taxon to a synonym of the given accepted usage.
   * All included descendants, both synonyms and accepted children, are also changed to become synonyms of the accepted.
   * @param u
   * @param accepted
   * @param synStatus
   * @param issue
   */
  public void convertToSynonym(NubUsage u, NubUsage accepted, TaxonomicStatus synStatus, @Nullable NameUsageIssue issue) {
    if (u.rank == Rank.GENUS || u.rank.isSuprageneric()) {
      // pretty high ranks, warn!
      LOG.warn("Converting {} into a {} of {}", u, synStatus, accepted);
    } else {
      LOG.info("Convert {} into a {} of {}", u, synStatus, accepted);
    }
    // convert to synonym, removing old parent relation
    // See http://dev.gbif.org/issues/browse/POR-398
    // change status
    u.status = synStatus;
    // flag issue if given
    if (issue != null){
      u.issues.add(issue);
    }

    // synonymize all descendants!
    Set<Node> nodes = Sets.newHashSet(u.node);
    for (Node d : Traversals.TREE_WITHOUT_PRO_PARTE.traverse(u.node).nodes()) {
      nodes.add(d);
    }
    for (Node s : nodes) {
      s.addLabel(Labels.SYNONYM);
      NubUsage su = dao.readNub(s);
      LOG.info("Also convert descendant {} into a synonym of {}", su, accepted);
      su.status = TaxonomicStatus.SYNONYM;
      for (Relationship rel : s.getRelationships(RelType.SYNONYM_OF, RelType.PARENT_OF)) {
        rel.delete();
      }
      // delete pro parte rel if they link to one of the synonym nodes
      for (Relationship rel : s.getRelationships(RelType.PROPARTE_SYNONYM_OF, Direction.OUTGOING)) {
        if (nodes.contains(rel.getOtherNode(s)) || rel.getOtherNode(s).equals(accepted)) {
          rel.delete();
        } else {
          su.status = TaxonomicStatus.PROPARTE_SYNONYM;
        }
      }
      // if this was the accepted for another pro parte synonym, route it to the newly accepted
      for (Relationship rel : s.getRelationships(RelType.PROPARTE_SYNONYM_OF, Direction.INCOMING)) {
        rel.getOtherNode(s).createRelationshipTo(accepted.node, RelType.PROPARTE_SYNONYM_OF);
        rel.delete();
      }
      s.createRelationshipTo(accepted.node, RelType.SYNONYM_OF);
      store(su);
    }
    // persist usage instance changes
    store(u);
  }


  /**
   * Iterates over all direct children of a node, deletes that parentOf relation and creates a new parentOf relation to the given new parent node instead.
   *
   * @param n      the node with child nodes
   * @param parent the new parent to be linked
   */
  @Deprecated
  private void moveChildren(Node n, NubUsage parent, NameUsageIssue... issues) {
    for (Relationship rel : Traversals.CHILDREN.traverse(n).relationships()) {
      Node child = rel.getOtherNode(n);
      rel.delete();
      // read nub usage to add an issue to the child
      NubUsage cu = dao.readNub(child);
      createParentRelation(cu, parent);
      Collections.addAll(cu.issues, issues);
      if (!cu.parsedName.getGenusOrAbove().equals(parent.parsedName.getGenusOrAbove())) {
        cu.issues.add(NameUsageIssue.NAME_PARENT_MISMATCH);
      }
      store(cu);
    }
  }

  /**
   * Iterates over all direct synonyms (both synonymOf and proParteSynonymOf) of a node, deletes that relationship
   * and creates a new relation of the same type to the given new accepted node instead.
   *
   * @param n        the node with synonym nodes
   * @param accepted the new accepted to be linked
   */
  @Deprecated
  private void moveSynonyms(Node n, Node accepted) {
    moveSynonyms(n, accepted, RelType.SYNONYM_OF);
    moveSynonyms(n, accepted, RelType.PROPARTE_SYNONYM_OF);
  }

  @Deprecated
  private void moveSynonyms(Node n, Node accepted, RelType type) {
    Set<Node> synonyms = Sets.newHashSet();
    for (Relationship rel : n.getRelationships(type, Direction.INCOMING)) {
      Node syn = rel.getOtherNode(n);
      rel.delete();
      synonyms.add(syn);
    }
    for (Node syn : synonyms) {
      syn.createRelationshipTo(accepted, type);
    }
  }

  public NubUsage store(NubUsage nub) {
    dao.store(nub);
    if (nub.rank == Rank.KINGDOM) {
      kingdoms.put(nub.kingdom, nub);
    }
    return nub;
  }

  /**
   * @param n      the node to start the parental hierarchy search from
   * @param search the node to find in the hierarchy
   */
  public boolean existsInClassification(Node n, Node search) {
    if (n.equals(search)) {
      return true;
    }
    if (n.hasLabel(Labels.SYNONYM)) {
      n = getParent(n);
      if (n.equals(search)) {
        return true;
      }
    }
    for (Node p : Traversals.PARENTS.traverse(n).nodes()) {
      if (p.equals(search)) {
        return true;
      }
    }
    return false;
  }

  public List<NubUsage> listBasionymGroup(Node bas) {
    List<NubUsage> group = Lists.newArrayList();
    for (Node n : IteratorUtil.loop(Traversals.BASIONYM_GROUP.traverse(bas).nodes().iterator())) {
      try {
        group.add(dao.readNub(n));
      } catch (NotFoundException e) {
        LOG.info("Basionym group member {} was removed. Ignore", n.getId());
      }
    }
    return group;
  }
}
