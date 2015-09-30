package org.gbif.checklistbank.nub.source;

import org.gbif.utils.file.InputStreamUtils;

import java.io.InputStream;
import java.util.UUID;

import org.apache.commons.io.IOUtils;

/**
 * UsageSource implementation that works on classpath files for testing nub builds.
 * This class is just a test helper class, see NubBuilderTest for its use.
 * For every dataset source there needs to be simple flat usage tab file under resources/nub-sources
 * with the following columns:
 * This abstract class reads a tab delimited text stream expected with the following columns:
 * <ul>
 * <li>usageKey</li>
 * <li>parentKey</li>
 * <li>basionymKey</li>
 * <li>rank (enum)</li>
 * <li>isSynonym (f/t)</li>
 * <li>taxonomicStatus (enum)</li>
 * <li>nomenclaturalStatus (enum[])</li>
 * <li>scientificName</li>
 * </ul>
 */
public class ClasspathSource extends NubSource {
    public final int id;

    public ClasspathSource(int id) {
        this(id, id);
    }

    public ClasspathSource(int id, int priority) {
        this.id = id;
        this.priority = priority;
        key = UUID.randomUUID();
        name = "Dataset " + id;
    }

    @Override
    void initNeo(NeoUsageWriter writer) throws Exception {
        String file = "nub-sources/dataset" + id + ".txt";
        InputStreamUtils isu = new InputStreamUtils();
        try (InputStream is = isu.classpathStream(file)) {
            IOUtils.copy(is, writer);
        }
    }
}