package org.gbif.checklistbank.neo.model;

import org.gbif.api.model.common.LinneanClassificationKeys;
import org.gbif.api.util.ClassificationUtils;
import org.gbif.api.vocabulary.Rank;

import com.google.common.base.Objects;

/**
 * Created by markus on 17/06/15.
 */
public class ClassificationKeys implements LinneanClassificationKeys {
    // for LinneanClassificationKeys
    private Integer kingdomKey;
    private Integer phylumKey;
    private Integer classKey;
    private Integer orderKey;
    private Integer familyKey;
    private Integer genusKey;
    private Integer subgenusKey;
    private Integer speciesKey;


    @Override
    public Integer getKingdomKey() {
        return kingdomKey;
    }

    @Override
    public void setKingdomKey(Integer kingdomKey) {
        this.kingdomKey = kingdomKey;
    }

    @Override
    public Integer getPhylumKey() {
        return phylumKey;
    }

    @Override
    public void setPhylumKey(Integer phylumKey) {
        this.phylumKey = phylumKey;
    }

    @Override
    public Integer getClassKey() {
        return classKey;
    }

    @Override
    public void setClassKey(Integer classKey) {
        this.classKey = classKey;
    }

    @Override
    public Integer getOrderKey() {
        return orderKey;
    }

    @Override
    public void setOrderKey(Integer orderKey) {
        this.orderKey = orderKey;
    }

    @Override
    public Integer getFamilyKey() {
        return familyKey;
    }

    @Override
    public void setFamilyKey(Integer familyKey) {
        this.familyKey = familyKey;
    }

    @Override
    public Integer getGenusKey() {
        return genusKey;
    }

    @Override
    public void setGenusKey(Integer genusKey) {
        this.genusKey = genusKey;
    }

    @Override
    public Integer getSubgenusKey() {
        return subgenusKey;
    }

    @Override
    public void setSubgenusKey(Integer subgenusKey) {
        this.subgenusKey = subgenusKey;
    }

    @Override
    public Integer getSpeciesKey() {
        return speciesKey;
    }

    @Override
    public void setSpeciesKey(Integer speciesKey) {
        this.speciesKey = speciesKey;
    }

    @Override
    public Integer getHigherRankKey(Rank rank) {
        return ClassificationUtils.getHigherRankKey(this, rank);
    }

    @Override
    public int hashCode() {
        return Objects
                .hashCode(
                        kingdomKey,
                        phylumKey,
                        classKey,
                        orderKey,
                        familyKey,
                        genusKey,
                        subgenusKey,
                        speciesKey);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ClassificationKeys other = (ClassificationKeys) obj;
        return Objects.equal(this.kingdomKey, other.kingdomKey)
                && Objects.equal(this.phylumKey, other.phylumKey)
                && Objects.equal(this.classKey, other.classKey)
                && Objects.equal(this.orderKey, other.orderKey)
                && Objects.equal(this.familyKey, other.familyKey)
                && Objects.equal(this.genusKey, other.genusKey)
                && Objects.equal(this.subgenusKey, other.subgenusKey)
                && Objects.equal(this.speciesKey, other.speciesKey);
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("kingdomKey", kingdomKey)
                .add("phylumKey", phylumKey)
                .add("classKey", classKey)
                .add("orderKey", orderKey)
                .add("familyKey", familyKey)
                .add("genusKey", genusKey)
                .add("subgenusKey", subgenusKey)
                .add("speciesKey", speciesKey)
                .toString();
    }
}