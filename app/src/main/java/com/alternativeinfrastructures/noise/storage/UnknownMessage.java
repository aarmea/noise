package com.alternativeinfrastructures.noise.storage;

import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.data.Blob;
import com.raizlabs.android.dbflow.structure.BaseModel;

@Table(database = MessageDatabase.class)
public class UnknownMessage extends BaseModel {
    // TODO: Design and implement database syncing across devices

    @PrimaryKey
    private Blob data;

    public Blob getData() {
        return data;
    }

    public void setData(Blob data) {
        this.data = data;
    }

    // Raw encrypted data, used only for debugging purposes
    public String toString() {
        String dataString = new String(this.data.getBlob());
        return dataString;
    }
}
