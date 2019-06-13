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
import com.raizlabs.android.dbflow.list.FlowCursorList;
import com.raizlabs.android.dbflow.list.FlowQueryList;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import com.alternativeinfrastructures.noise.storage.UnknownMessage;
import com.alternativeinfrastructures.noise.storage.UnknownMessage_Table;

import java.util.UUID;

public class RawMessageList extends AppCompatActivity {
    public static final String TAG = "RawMessageList";

    private static final byte TEST_ZERO_BITS = 10;
    private static final UUID TEST_TYPE = new UUID(0, 0);

    private FlowQueryList<UnknownMessage> messages;
    private ArrayAdapter<UnknownMessage> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_raw_message_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setTitle(R.string.raw_message_view_title);

        // TODO: Use a query list that's smarter about not copying the entire list at once
        messages = SQLite.select().from(UnknownMessage.class).orderBy(UnknownMessage_Table.payload, true).flowQueryList();
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

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    byte[] payload = "This is an unencrypted test message".getBytes();
                    UnknownMessage.Companion.rawCreateAndSignAsync(payload, TEST_ZERO_BITS, TEST_TYPE).subscribe();
                } catch (UnknownMessage.PayloadTooLargeException e) {
                    Log.e(TAG, "Message not created", e);
                }
            }
        });
    }
}
