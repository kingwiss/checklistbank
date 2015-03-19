package org.gbif.checklistbank.utils;

import org.gbif.api.model.checklistbank.VerbatimNameUsage;
import org.gbif.api.vocabulary.Extension;
import org.gbif.dwc.terms.AcTerm;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializing/Deserializing tool specifically for the term maps of a VerbatimNameUsage to be stored in postgres
 * or neo backends as a single binary column.
 */
public class VerbatimNameUsageMapperKryo implements VerbatimNameUsageMapper{

  private static final Logger LOG = LoggerFactory.getLogger(VerbatimNameUsageMapperKryo.class);
  private final Kryo kryo;

  public VerbatimNameUsageMapperKryo() {
    kryo = new Kryo();
    kryo.setRegistrationRequired(false);
    kryo.register(VerbatimNameUsage.class);
    kryo.register(Date.class);
    kryo.register(HashMap.class);
    kryo.register(ArrayList.class);
    kryo.register(Extension.class);
    kryo.register(DwcTerm.class);
    kryo.register(DcTerm.class);
    kryo.register(GbifTerm.class);
    kryo.register(AcTerm.class);
  }

  @Override
  public VerbatimNameUsage read(byte[] bytes) {
    if (bytes != null) {
      final Input input = new Input(bytes);
      return kryo.readObject(input, VerbatimNameUsage.class);
    }
    return null;
  }

  @Override
  public byte[] write(VerbatimNameUsage verbatim) {
    if (verbatim != null) {
      final Output output = new Output(4096, -1);
      kryo.writeObject(output, verbatim);
      output.flush();
      return output.getBuffer();
    }
    return null;
  }
}