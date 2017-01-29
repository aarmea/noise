package org.thoughtcrime.meshmessenger;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.raizlabs.android.dbflow.config.DatabaseDefinition;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.data.Blob;
import com.raizlabs.android.dbflow.list.FlowCursorList;
import com.raizlabs.android.dbflow.list.FlowQueryList;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;
import com.raizlabs.android.dbflow.structure.database.transaction.Transaction;

import org.thoughtcrime.meshmessenger.storage.MessageDatabase;
import org.thoughtcrime.meshmessenger.storage.UnknownMessage;
import org.thoughtcrime.meshmessenger.storage.UnknownMessage_Table;

import java.util.UUID;

public class RawMessageList extends AppCompatActivity {
    public static final String TAG = "RawMessageList";

    private DatabaseDefinition messageDb;
    private FlowQueryList<UnknownMessage> messages;
    private ArrayAdapter<UnknownMessage> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        messageDb = FlowManager.getDatabase(MessageDatabase.class);

        setContentView(R.layout.activity_raw_message_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setTitle(R.string.app_name);

        // TODO: Use a query list that's smarter about not copying the entire list at once
        // TODO: Make the actual messages table and hook up this ListView to that instead
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

        // XXX Prototyping only
        final Transaction makeDummyMessageTransaction = messageDb.beginTransactionAsync(new ITransaction() {
            @Override
            public void execute(DatabaseWrapper databaseWrapper) {
                Blob messageBlob = new Blob();
                messageBlob.setBlob(UUID.randomUUID().toString().getBytes());
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_raw_message_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
