package com.alternativeinfrastructures.noise.views;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.alternativeinfrastructures.noise.R;
import com.alternativeinfrastructures.noise.models.LocalIdentity;
import com.alternativeinfrastructures.noise.util.TextValidator;

public class NewIdentityActivity extends AppCompatActivity {
    public static final String TAG = "NewIdentityActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_identity);
        setTitle(R.string.create_identity_title);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        EditText usernameEdit = (EditText) findViewById(R.id.username);
        Button createIdentityButton = (Button) findViewById(R.id.createIdentityButton);
        String usernameRequirements = getString(R.string.username_requirements);

        usernameEdit.addTextChangedListener(new TextValidator(usernameEdit) {
            @Override
            public void validate(TextView textView, String text) {
                boolean valid = LocalIdentity.validUsername(text);
                textView.setError(valid ? null : usernameRequirements);
                createIdentityButton.setEnabled(valid);
            }
        });

        createIdentityButton.setOnClickListener((View view) -> {
            String username = usernameEdit.getText().toString();
            Log.d(TAG, "Creating identity with username: " + username);

            try {
                LocalIdentity.createNew(username).subscribe();
                // TODO: UI showing that we're waiting for the identity message to sign
                // TODO: Close the view once we have an identity
            } catch (Exception e) {
                Log.e(TAG, "Error creating an identity", e);
            }
        });
    }

    // TODO: Warn (with a tooltip or similar) if the username already exists

    // TODO: How should we handle moving to a new device?
    // IdentityMigration message? It can be signed using the old device's private key
}
