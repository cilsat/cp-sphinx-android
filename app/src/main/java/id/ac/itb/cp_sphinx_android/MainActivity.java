package id.ac.itb.cp_sphinx_android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageButton;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;

public class MainActivity extends AppCompatActivity implements
        RecognitionListener{

    /* Named search */
    private static final String KWS_SEARCH = "wakeup";

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    /* Declare speech recognizer */
    private SpeechRecognizerPlay recognizer;
    private HashMap<String, Integer> captions;

    /* Button to start recognition */
    ImageButton button;
    private boolean isPlaying = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        // Prepare the data for UI
        captions = new HashMap<>();
        captions.put(KWS_SEARCH, R.string.kws_caption);
        setContentView(R.layout.activity_main);
        ((TextView) findViewById(R.id.caption_text))
                .setText("Preparing the recognizer");

        button = (ImageButton) findViewById(R.id.button);
        button.setEnabled(false);

        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        runRecognizerSetup();

        // Set up button listener to start/stop recognition
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (!isPlaying) {
                        recognizer.startListening(KWS_SEARCH);
                        button.setPressed(true);
                        isPlaying = true;
                    }
                    else {
                        recognizer.stop();
                        button.setPressed(false);
                        isPlaying = false;
                    }
                }
                return true;
            }
        });
    }

    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(MainActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    ((TextView) findViewById(R.id.caption_text))
                            .setText("Failed to init recognizer " + result);
                } else {
                    button.setEnabled(true);
                    ((TextView) findViewById(R.id.caption_text))
                            .setText("");
                }
            }
        }.execute();
    }

    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        recognizer = SpeechRecognizerPlaySetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "semi-250"))
                .setDictionary(new File(assetsDir, "cp-sphinx.dic"))
                //.setRawLogDir(assetsDir) // Logging of raw audio
                .getRecognizer();
        recognizer.addListener(this);

        // Create keyword-activation search.
        File languageModel = new File(assetsDir, "cp-sphinx.arpa");
        recognizer.addNgramSearch(KWS_SEARCH, languageModel);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runRecognizerSetup();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    /**
     * In partial result we get quick updates about current hypothesis. In
     * keyword spotting mode we can react here, in other modes we need to wait
     * for final result in onResult.
     */
    @Override
    public void onPartialResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            ((TextView) findViewById(R.id.result_text)).setText(text);
        } else {
            ((TextView) findViewById(R.id.result_text)).setText("...");
        }
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            ((TextView) findViewById(R.id.result_text)).setText(text);
            //makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
        }
        else
            ((TextView) findViewById(R.id.result_text)).setText("Coba lagi");
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    /**
     * We stop recognizer here to get a final result
     */
    @Override
    public void onEndOfSpeech() {
    }

    @Override
    public void onError(Exception error) {
        ((TextView) findViewById(R.id.caption_text)).setText(error.getMessage());
    }

    @Override
    public void onTimeout() {
    }
}
