package org.gbif.checklistbank.index;

import org.gbif.api.model.checklistbank.NameUsageContainer;
import org.gbif.api.model.common.paging.PagingRequest;
import org.gbif.api.service.checklistbank.DescriptionService;
import org.gbif.api.service.checklistbank.DistributionService;
import org.gbif.api.service.checklistbank.SpeciesProfileService;
import org.gbif.api.service.checklistbank.VernacularNameService;
import org.gbif.checklistbank.service.UsageService;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.google.inject.Inject;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.common.SolrInputDocument;

/**
 * Service that updates a solr checklistbank index in real time.
 */
public class NameUsageIndexServiceSolr implements NameUsageIndexService {

  private final NameUsageDocConverter converter = new NameUsageDocConverter();
  private final SolrServer solr;
  private final int commitWithinMs = 60*1000;
  private final UsageService usageService;
  private final VernacularNameService vernacularNameService;
  private final DescriptionService descriptionService;
  private final DistributionService distributionService;
  private final SpeciesProfileService speciesProfileService;
  // consider only some extension records at most
  private final PagingRequest page = new PagingRequest(0, 500);

  @Inject
  public NameUsageIndexServiceSolr(
    SolrServer solr,
    UsageService usageService,
    VernacularNameService vernacularNameService,
    DescriptionService descriptionService,
    DistributionService distributionService,
    SpeciesProfileService speciesProfileService
  ) {
    this.solr = solr;
    this.usageService = usageService;
    this.vernacularNameService = vernacularNameService;
    this.descriptionService = descriptionService;
    this.distributionService = distributionService;
    this.speciesProfileService = speciesProfileService;
  }

  @Override
  public void delete(int usageKey) {
    try {
      solr.deleteById(String.valueOf(usageKey), commitWithinMs);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void delete(UUID datasetKey) {
    try {
      solr.deleteByQuery("dataset_key:"+datasetKey.toString(), commitWithinMs);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void insertOrUpdate(int key) {
    // we use the list service for just one record cause its more effective
    // leaving out fields that we do not index in solr
    List<NameUsageContainer> range = usageService.listRange(key, key);
    if (!range.isEmpty()) {
      NameUsageContainer u = range.get(0);
      u.setDistributions(distributionService.listByUsage(key, page).getResults());
      u.setDescriptions(descriptionService.listByUsage(key, page).getResults());
      u.setVernacularNames(vernacularNameService.listByUsage(key, page).getResults());
      u.setSpeciesProfiles(speciesProfileService.listByUsage(key, page).getResults());

      insertOrUpdate(u, usageService.listParents(key));
    }
  }

  //TODO: do this asynchroneously?
  @Override
  public void insertOrUpdate(Collection<Integer> usageKeys) {
    for (Integer key : usageKeys) {
      if (key != null) {
        insertOrUpdate(key);
      }
    }
  }

  @Override
  public void insertOrUpdate(NameUsageContainer usage, List<Integer> parentKeys) {
    SolrInputDocument doc = converter.toObject(usage, parentKeys);
    try {
      solr.add(doc, commitWithinMs);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
