package com.alternativeinfrastructures.noise.views;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.SearchView;

import com.alternativeinfrastructures.noise.R;

public class NewConversationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.new_conversation_title);
        setContentView(R.layout.activity_new_conversation);

        SearchView input = (SearchView) findViewById(R.id.contacts_search_input);
        // TODO: Register for changes and update the list
        // TODO: Can we make the SearchView do this for us instead?
    }
}