package id.ac.itb.cp_sphinx_android;

/**
 * Created by cilsat on 5/12/17.
 */

import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import java.io.File;
import java.io.IOException;

public class SpeechRecognizerPlaySetup {
    private final Config config;

    public static SpeechRecognizerPlaySetup defaultSetup() {
        return new SpeechRecognizerPlaySetup(Decoder.defaultConfig());
    }

    public static SpeechRecognizerPlaySetup setupFromFile(File configFile) {
        return new SpeechRecognizerPlaySetup(Decoder.fileConfig(configFile.getPath()));
    }

    private SpeechRecognizerPlaySetup(Config config) {
        this.config = config;
    }

    public SpeechRecognizerPlay getRecognizer() throws IOException {
        return new SpeechRecognizerPlay(this.config);
    }

    public SpeechRecognizerPlaySetup setAcousticModel(File model) {
        return this.setString("-hmm", model.getPath());
    }

    public SpeechRecognizerPlaySetup setDictionary(File dictionary) {
        return this.setString("-dict", dictionary.getPath());
    }

    public SpeechRecognizerPlaySetup setSampleRate(int rate) {
        return this.setFloat("-samprate", (double)rate);
    }

    public SpeechRecognizerPlaySetup setRawLogDir(File dir) {
        return this.setString("-rawlogdir", dir.getPath());
    }

    public SpeechRecognizerPlaySetup setKeywordThreshold(float threshold) {
        return this.setFloat("-kws_threshold", (double)threshold);
    }

    public SpeechRecognizerPlaySetup setBoolean(String key, boolean value) {
        this.config.setBoolean(key, value);
        return this;
    }

    public SpeechRecognizerPlaySetup setInteger(String key, int value) {
        this.config.setInt(key, value);
        return this;
    }

    public SpeechRecognizerPlaySetup setFloat(String key, double value) {
        this.config.setFloat(key, value);
        return this;
    }

    public SpeechRecognizerPlaySetup setString(String key, String value) {
        this.config.setString(key, value);
        return this;
    }

    static {
        System.loadLibrary("pocketsphinx_jni");
    }
}
