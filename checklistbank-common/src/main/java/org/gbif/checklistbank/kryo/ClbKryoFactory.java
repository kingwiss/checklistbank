package org.gbif.checklistbank.kryo;

import org.gbif.api.model.checklistbank.DatasetMetrics;
import org.gbif.api.model.checklistbank.Description;
import org.gbif.api.model.checklistbank.Distribution;
import org.gbif.api.model.checklistbank.NameUsage;
import org.gbif.api.model.checklistbank.NameUsageMediaObject;
import org.gbif.api.model.checklistbank.NameUsageMetrics;
import org.gbif.api.model.checklistbank.ParsedName;
import org.gbif.api.model.checklistbank.Reference;
import org.gbif.api.model.checklistbank.SpeciesProfile;
import org.gbif.api.model.checklistbank.TypeSpecimen;
import org.gbif.api.model.checklistbank.VerbatimNameUsage;
import org.gbif.api.model.checklistbank.VernacularName;
import org.gbif.api.model.common.Identifier;
import org.gbif.api.vocabulary.CitesAppendix;
import org.gbif.api.vocabulary.Country;
import org.gbif.api.vocabulary.EstablishmentMeans;
import org.gbif.api.vocabulary.Extension;
import org.gbif.api.vocabulary.IdentifierType;
import org.gbif.api.vocabulary.Kingdom;
import org.gbif.api.vocabulary.Language;
import org.gbif.api.vocabulary.LifeStage;
import org.gbif.api.vocabulary.MediaType;
import org.gbif.api.vocabulary.NamePart;
import org.gbif.api.vocabulary.NameType;
import org.gbif.api.vocabulary.NameUsageIssue;
import org.gbif.api.vocabulary.NomenclaturalStatus;
import org.gbif.api.vocabulary.OccurrenceStatus;
import org.gbif.api.vocabulary.Origin;
import org.gbif.api.vocabulary.Rank;
import org.gbif.api.vocabulary.Sex;
import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.api.vocabulary.ThreatStatus;
import org.gbif.api.vocabulary.TypeDesignationType;
import org.gbif.api.vocabulary.TypeStatus;
import org.gbif.checklistbank.model.UsageExtensions;
import org.gbif.dwc.terms.AcTerm;
import org.gbif.dwc.terms.DcElement;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.EolReferenceTerm;
import org.gbif.dwc.terms.GbifInternalTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.IucnTerm;
import org.gbif.dwc.terms.UnknownTerm;
import org.gbif.dwc.terms.XmpRightsTerm;
import org.gbif.dwc.terms.XmpTerm;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.google.common.collect.ImmutableList;

/**
 * Creates a kryo factory usable for thread safe kryo pools that can deal with clb api classes.
 * We use Kryo for extremely fast byte serialization of objects:
 * 1) We store in postgres a byte array for the verbatim usage instances.
 * 2) We use kryo to serialize various information in kvp stores during checklist indexing and nub builds.
 *
 * The serialization format of kryo stays the same over minor version changes, so we do not need to reindex checklists
 * just because we update the kryo library. Make sure to not update to an incompatible format change, see kryo changes logs:
 * https://github.com/EsotericSoftware/kryo/blob/master/CHANGES.md
 *
 * CAUTION! We require registration of all classes that kryo should be able to handle.
 * This registration reduces the resulting binary size and improves performance,
 * BUT the registered integers must stay the same over time or otherwise existing data in unreadable.
 */
public class ClbKryoFactory implements KryoFactory {

    @Override
    public Kryo create() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(true);

        kryo.register(NameUsage.class, 0);
        kryo.register(VerbatimNameUsage.class, 1);
        kryo.register(NameUsageMetrics.class, 2);
        kryo.register(UsageExtensions.class, 3);
        kryo.register(ParsedName.class, 4);
        kryo.register(DatasetMetrics.class, 5);
        kryo.register(Description.class, 6);
        kryo.register(Distribution.class, 7);
        kryo.register(Identifier.class, 8);
        kryo.register(NameUsageMediaObject.class, 9);
        kryo.register(Reference.class, 10);
        kryo.register(SpeciesProfile.class, 11);
        kryo.register(TypeSpecimen.class, 12);
        kryo.register(VernacularName.class, 13);

        // java & commons
        kryo.register(Date.class, 20);
        kryo.register(HashMap.class, 21);
        kryo.register(HashSet.class, 22);
        kryo.register(ArrayList.class, 23);
        kryo.register(UUID.class, new UUIDSerializer(), 24);
        kryo.register(URI.class, new URISerializer(), 25);
        kryo.register(ImmutableList.class, new ImmutableListSerializer(), 26);

        // enums
        kryo.register(EnumSet.class, new EnumSetSerializer(), 30);
        kryo.register(NameUsageIssue.class, 31);
        kryo.register(NomenclaturalStatus.class, 32);
        kryo.register(NomenclaturalStatus[].class, 33);
        kryo.register(TaxonomicStatus.class, 34);
        kryo.register(Origin.class, 35);
        kryo.register(Rank.class, 36);
        kryo.register(Extension.class, 37);
        kryo.register(Kingdom.class, 38);
        kryo.register(NameType.class, 39);
        kryo.register(NamePart.class,40);
        kryo.register(Language.class, 41);
        kryo.register(Country.class, 42);
        kryo.register(OccurrenceStatus.class, 43);
        kryo.register(LifeStage.class, 44);
        kryo.register(ThreatStatus.class, 45);
        kryo.register(EstablishmentMeans.class, 46);
        kryo.register(CitesAppendix.class, 47);
        kryo.register(IdentifierType.class, 48);
        kryo.register(MediaType.class, 49);
        kryo.register(TypeStatus.class, 50);
        kryo.register(TypeDesignationType.class, 51);
        kryo.register(Sex.class, 52);
        // term enums
        kryo.register(AcTerm.class, 53);
        kryo.register(DcElement.class, 54);
        kryo.register(DcTerm.class, 55);
        kryo.register(DwcTerm.class, 56);
        kryo.register(EolReferenceTerm.class, 57);
        kryo.register(GbifInternalTerm.class, 58);
        kryo.register(GbifTerm.class, 59);
        kryo.register(IucnTerm.class, 60);
        kryo.register(XmpRightsTerm.class, 61);
        kryo.register(XmpTerm.class, 62);
        kryo.register(UnknownTerm.class, new TermSerializer(), 63);

        return kryo;
    }
}