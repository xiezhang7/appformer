package org.livespark.formmodeler.model.impl;

import org.livespark.formmodeler.model.FieldDefinition;

/**
 * Created by pefernan on 4/29/15.
 */
public abstract class AbstractIntputFieldDefinition extends FieldDefinition {
    protected Integer size = 15;
    protected Integer maxLength = 100;
    protected String placeHolder;

    public Integer getSize() {
        return size;
    }

    public void setSize( Integer size ) {
        this.size = size;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength( Integer maxLength ) {
        this.maxLength = maxLength;
    }

    public String getPlaceHolder() {
        return placeHolder;
    }

    public void setPlaceHolder( String placeHolder ) {
        this.placeHolder = placeHolder;
    }
}
