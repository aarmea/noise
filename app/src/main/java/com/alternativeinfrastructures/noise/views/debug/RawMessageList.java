package com.alternativeinfrastructures.noise.views.debug;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.alternativeinfrastructures.noise.R;
import com.raizlabs.android.dbflow.config.DatabaseDefinition;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.data.Blob;
import com.raizlabs.android.dbflow.list.FlowCursorList;
import com.raizlabs.android.dbflow.list.FlowQueryList;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;
import com.raizlabs.android.dbflow.structure.database.transaction.Transaction;

import com.alternativeinfrastructures.noise.storage.MessageDatabase;
import com.alternativeinfrastructures.noise.storage.UnknownMessage;
import com.alternativeinfrastructures.noise.storage.UnknownMessage_Table;

import java.math.BigInteger;
import java.security.SecureRandom;

public class RawMessageList extends AppCompatActivity {
    public static final String TAG = "RawMessageList";

    private DatabaseDefinition messageDb;
    private FlowQueryList<UnknownMessage> messages;
    private ArrayAdapter<UnknownMessage> adapter;

    private SecureRandom random = new SecureRandom();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        messageDb = FlowManager.getDatabase(MessageDatabase.class);

        setContentView(R.layout.activity_raw_message_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setTitle(R.string.raw_message_view_title);

        // TODO: Use a query list that's smarter about not copying the entire list at once
        messages = SQLite.select().from(UnknownMessage.class).orderBy(UnknownMessage_Table.data, true).flowQueryList();
        messages.registerForContentChanges(this);
        adapter = new ArrayAdapter<UnknownMessage>(this, android.R.layout.simple_list_item_1, messages);
        messages.addOnCursorRefreshListener(new FlowCursorList.OnCursorRefreshListener<UnknownMessage>() {
            @Override
            public void onCursorRefreshed(FlowCursorList<UnknownMessage> cursorList) {
                Log.d(TAG, "Displaying new unknown message");
                adapter.notifyDataSetChanged();
            }
        });

        ListView listView = (ListView) findViewById(R.id.content_raw_message_list_view);
        listView.setAdapter(adapter);

        final Transaction makeDummyMessageTransaction = messageDb.beginTransactionAsync(new ITransaction() {
            @Override
            public void execute(DatabaseWrapper databaseWrapper) {
                Blob messageBlob = new Blob();
                messageBlob.setBlob(new BigInteger(1024, random).toByteArray());
                UnknownMessage message = new UnknownMessage();
                message.setData(messageBlob);
                message.save(databaseWrapper);
            }
        }).success(new Transaction.Success() {
            @Override
            public void onSuccess(Transaction transaction) {
                Log.d(TAG, "Generated an unknown message in storage");
            }
        }).build();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeDummyMessageTransaction.execute();
            }
        });
    }
}
