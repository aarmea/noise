package com.alternativeinfrastructures.noise.views;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.alternativeinfrastructures.noise.R;
import com.alternativeinfrastructures.noise.models.LocalIdentity;
import com.alternativeinfrastructures.noise.views.debug.RawMessageList;
import com.raizlabs.android.dbflow.sql.language.SQLite;

public class ConversationList extends AppCompatActivity {
    public static final String TAG = "ConversationList";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation_list);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setTitle(R.string.conversation_view_title);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        if (SQLite.selectCountOf().from(LocalIdentity.class).count() < 1) {
            Log.d(TAG, "No identities exist, creating a new one");
            startActivity(new Intent(this, NewIdentityActivity.class));
        }
    }

    // TODO: Implement conversations here

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_conversation_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // TODO: Hide the debug-oriented options in release builds
        switch (item.getItemId()) {
            case R.id.action_raw_message_list:
                startActivity(new Intent(this, RawMessageList.class));
                return true;
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
