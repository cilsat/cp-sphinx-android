package id.ac.itb.cp_sphinx_android;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.ImageButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import edu.cmu.pocketsphinx.*;

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
    private ImageButton button;
    private boolean isPlaying = false;

    /* Audio buffering manager */
    private HashMap<String, Integer> rawMap;
    private SoundPool soundPool;

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
        createRecognizer();

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

    private void createRecognizer() {
        // Recognizer initialization is a time-consuming process and it involves IO,
        // so we execute it in an async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(MainActivity.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);
                    createSoundPool(assetDir);
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

    private void createSoundPool(File assetsDir) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            createNewSoundPool();
        } else {
            createOldSoundPool();
        }

        File dict = new File(assetsDir, "cp-sphinx.dic");
        rawMap = new HashMap<>();
        try(BufferedReader br = new BufferedReader(new FileReader(dict))) {
            Context context = getApplicationContext();
            String packageName = getPackageName();
            String line = br.readLine();
            while (line != null) {
                String name = line.split(" ")[0];
                //Log.d("NAME", name);
                int rawID = getResources().getIdentifier(name, "raw", packageName);
                int rawVal = soundPool.load(context, rawID, 1);
                rawMap.put(name, rawVal);
                line = br.readLine();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void createNewSoundPool() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        soundPool = new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(5)
                .build();
    }

    @SuppressWarnings("deprecation")
    private void createOldSoundPool() {
        soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createRecognizer();
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

        if (soundPool != null) {
            soundPool.release();
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
            Log.d("HYP", text);
            String[] splits = text.split(" ");
            if (splits.length > 1) text = splits[splits.length - 1];
            ((TextView) findViewById(R.id.result_text)).setText(text);
        } else {
            ((TextView) findViewById(R.id.result_text)).setText("...");
        }
    }

    /**
     * This callback is called when we stop the recognizer.
     */
    @Override
    public void onResult(Hypothesis hypothesis) throws RuntimeException {
        if (hypothesis != null) {
            String text = hypothesis.getHypstr();
            Log.d("RES", text);
            String[] splits = text.split(" ");
            if (splits.length > 1) text = splits[splits.length - 1];
            ((TextView) findViewById(R.id.result_text)).setText(text);
            if (rawMap.containsKey(text)) {
                soundPool.play(rawMap.get(text), 1.5f, 1.5f, 1, 0, 1f);
            }
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
