package com.alternativeinfrastructures.noise.storage;

import android.util.Base64;

import com.raizlabs.android.dbflow.annotation.PrimaryKey;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.data.Blob;
import com.raizlabs.android.dbflow.structure.BaseModel;

@Table(database = MessageDatabase.class)
public class UnknownMessage extends BaseModel {
    // TODO: Design and implement database syncing across devices

    @PrimaryKey
    private Blob data;

    // TODO: Numeric pad, date, etc. needed to implement some variant of hashcash
    // TODO: Implement a way to get a (Bloom filter?) bit string that describes the entire contents of this table (ideally directly in SQLite/DBFlow)

    public Blob getData() {
        return data;
    }

    public void setData(Blob data) {
        this.data = data;
    }

    // Raw encrypted data, used only for debugging purposes
    public String toString() {
        return Base64.encodeToString(this.data.getBlob(), Base64.NO_WRAP);
    }
}
