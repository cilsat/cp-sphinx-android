package id.ac.itb.cp_sphinx_android;

/**
 * Created by cilsat on 5/12/17.
 */

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import edu.cmu.pocketsphinx.Config;
import edu.cmu.pocketsphinx.Decoder;
import edu.cmu.pocketsphinx.FsgModel;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

public class SpeechRecognizerPlay {
    protected static final String TAG = SpeechRecognizerPlay.class.getSimpleName();
    private final Decoder decoder;
    private final int sampleRate;
    private int bufferSize;
    private final AudioRecord recorder;
    //private final AudioTrack track;
    private Thread recognizerThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Collection<RecognitionListener> listeners = new HashSet();

    protected SpeechRecognizerPlay(Config config) throws IOException {
        this.decoder = new Decoder(config);
        this.sampleRate = (int)this.decoder.getConfig().getFloat("-samprate");
        this.bufferSize = Math.round((float)this.sampleRate * 0.4F);
        this.recorder = new AudioRecord(6, this.sampleRate, 16, 2, this.bufferSize * 2);
        if(this.recorder.getState() == 0) {
            this.recorder.release();
            throw new IOException("Failed to initialize recorder. Microphone might be already in use.");
        }
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler echoCanceler = AcousticEchoCanceler.create(recorder.getAudioSessionId());
            echoCanceler.setEnabled(true);
            Log.i("ECHO", "Echo canceler available");
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor noiseSuppressor = NoiseSuppressor.create(recorder.getAudioSessionId());
            noiseSuppressor.setEnabled(true);
            Log.i("NOISE", "Noise suppressor available");
        }
        //this.track = new AudioTrack(AudioManager.STREAM_MUSIC, this.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, this.bufferSize * 2, AudioTrack.MODE_STREAM);
    }

    public void addListener(RecognitionListener listener) {
        Collection var2 = this.listeners;
        synchronized(this.listeners) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(RecognitionListener listener) {
        Collection var2 = this.listeners;
        synchronized(this.listeners) {
            this.listeners.remove(listener);
        }
    }

    public boolean startListening(String searchName) {
        if(null != this.recognizerThread) {
            return false;
        } else {
            Log.i(TAG, String.format("Start recognition \"%s\"", new Object[]{searchName}));
            this.decoder.setSearch(searchName);
            this.recognizerThread = new SpeechRecognizerPlay.RecognizerThread();
            this.recognizerThread.start();
            return true;
        }
    }

    public boolean startListening(String searchName, int timeout) {
        if(null != this.recognizerThread) {
            return false;
        } else {
            Log.i(TAG, String.format("Start recognition \"%s\"", new Object[]{searchName}));
            this.decoder.setSearch(searchName);
            this.recognizerThread = new SpeechRecognizerPlay.RecognizerThread(timeout);
            this.recognizerThread.start();
            return true;
        }
    }

    private boolean stopRecognizerThread() {
        if(null == this.recognizerThread) {
            return false;
        } else {
            try {
                this.recognizerThread.interrupt();
                this.recognizerThread.join();
            } catch (InterruptedException var2) {
                Thread.currentThread().interrupt();
            }

            this.recognizerThread = null;
            return true;
        }
    }

    public boolean stop() {
        boolean result = this.stopRecognizerThread();
        if(result) {
            Log.i(TAG, "Stop recognition");
            Hypothesis hypothesis = this.decoder.hyp();
            this.mainHandler.post(new SpeechRecognizerPlay.ResultEvent(hypothesis, true));
        }

        return result;
    }

    public boolean cancel() {
        boolean result = this.stopRecognizerThread();
        if(result) {
            Log.i(TAG, "Cancel recognition");
        }

        return result;
    }

    public Decoder getDecoder() {
        return this.decoder;
    }

    public void shutdown() {
        this.recorder.release();
        //this.track.release();
    }

    public String getSearchName() {
        return this.decoder.getSearch();
    }

    public void addFsgSearch(String searchName, FsgModel fsgModel) {
        this.decoder.setFsg(searchName, fsgModel);
    }

    public void addGrammarSearch(String name, File file) {
        Log.i(TAG, String.format("Load JSGF %s", new Object[]{file}));
        this.decoder.setJsgfFile(name, file.getPath());
    }

    public void addGrammarSearch(String name, String jsgfString) {
        this.decoder.setJsgfString(name, jsgfString);
    }

    public void addNgramSearch(String name, File file) {
        Log.i(TAG, String.format("Load N-gram model %s", new Object[]{file}));
        this.decoder.setLmFile(name, file.getPath());
    }

    public void addKeyphraseSearch(String name, String phrase) {
        this.decoder.setKeyphrase(name, phrase);
    }

    public void addKeywordSearch(String name, File file) {
        this.decoder.setKws(name, file.getPath());
    }

    public void addAllphoneSearch(String name, File file) {
        this.decoder.setAllphoneFile(name, file.getPath());
    }

    private class TimeoutEvent extends SpeechRecognizerPlay.RecognitionEvent {
        private TimeoutEvent() {
            super();
        }

        protected void execute(RecognitionListener listener) {
            listener.onTimeout();
        }
    }

    private class OnErrorEvent extends SpeechRecognizerPlay.RecognitionEvent {
        private final Exception exception;

        OnErrorEvent(Exception exception) {
            super();
            this.exception = exception;
        }

        protected void execute(RecognitionListener listener) {
            listener.onError(this.exception);
        }
    }

    private class ResultEvent extends SpeechRecognizerPlay.RecognitionEvent {
        protected final Hypothesis hypothesis;
        private final boolean finalResult;

        ResultEvent(Hypothesis hypothesis, boolean finalResult) {
            super();
            this.hypothesis = hypothesis;
            this.finalResult = finalResult;
        }

        protected void execute(RecognitionListener listener) {
            if(this.finalResult) {
                listener.onResult(this.hypothesis);
            } else {
                listener.onPartialResult(this.hypothesis);
            }

        }
    }

    private class InSpeechChangeEvent extends SpeechRecognizerPlay.RecognitionEvent {
        private final boolean state;

        InSpeechChangeEvent(boolean state) {
            super();
            this.state = state;
        }

        protected void execute(RecognitionListener listener) {
            if(this.state) {
                listener.onBeginningOfSpeech();
            } else {
                listener.onEndOfSpeech();
            }

        }
    }

    private abstract class RecognitionEvent implements Runnable {
        private RecognitionEvent() {
        }

        public void run() {
            RecognitionListener[] emptyArray = new RecognitionListener[0];
            RecognitionListener[] var2 = SpeechRecognizerPlay.this.listeners.toArray(emptyArray);
            int var3 = var2.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                RecognitionListener listener = var2[var4];
                this.execute(listener);
            }

        }

        protected abstract void execute(RecognitionListener var1);
    }

    private final class RecognizerThread extends Thread {
        private int remainingSamples;
        private int timeoutSamples;
        private static final int NO_TIMEOUT = -1;

        public RecognizerThread(int timeout) {
            if(timeout != -1) {
                this.timeoutSamples = timeout * SpeechRecognizerPlay.this.sampleRate / 1000;
            } else {
                this.timeoutSamples = -1;
            }

            this.remainingSamples = this.timeoutSamples;
        }

        public RecognizerThread() {
            this(-1);
        }

        public void run() {
            SpeechRecognizerPlay.this.recorder.startRecording();
            //SpeechRecognizerPlay.this.track.play();
            if(SpeechRecognizerPlay.this.recorder.getRecordingState() == 1) {
                SpeechRecognizerPlay.this.recorder.stop();
                //SpeechRecognizerPlay.this.track.pause();
                IOException buffer1 = new IOException("Failed to start recording. Microphone might be already in use.");
                SpeechRecognizerPlay.this.mainHandler.post(SpeechRecognizerPlay.this.new OnErrorEvent(buffer1));
            } else {
                Log.d(SpeechRecognizerPlay.TAG, "Starting decoding");
                SpeechRecognizerPlay.this.decoder.startUtt();
                short[] buffer = new short[SpeechRecognizerPlay.this.bufferSize];
                boolean inSpeech = SpeechRecognizerPlay.this.decoder.getInSpeech();
                SpeechRecognizerPlay.this.recorder.read(buffer, 0, buffer.length);

                while(!interrupted() && (this.timeoutSamples == -1 || this.remainingSamples > 0)) {
                    int nread = SpeechRecognizerPlay.this.recorder.read(buffer, 0, buffer.length);
                    if(-1 == nread) {
                        throw new RuntimeException("error reading audio buffer");
                    }

                    if(nread > 0) {
                        //SpeechRecognizerPlay.this.track.write(buffer, 0, nread);
                        SpeechRecognizerPlay.this.decoder.processRaw(buffer, (long)nread, false, false);
                        if(SpeechRecognizerPlay.this.decoder.getInSpeech() != inSpeech) {
                            inSpeech = SpeechRecognizerPlay.this.decoder.getInSpeech();
                            SpeechRecognizerPlay.this.mainHandler.post(SpeechRecognizerPlay.this.new InSpeechChangeEvent(inSpeech));
                        }

                        if(inSpeech) {
                            this.remainingSamples = this.timeoutSamples;
                        }

                        Hypothesis hypothesis = SpeechRecognizerPlay.this.decoder.hyp();
                        SpeechRecognizerPlay.this.mainHandler.post(SpeechRecognizerPlay.this.new ResultEvent(hypothesis, false));
                    }

                    if(this.timeoutSamples != -1) {
                        this.remainingSamples -= nread;
                    }
                }

                SpeechRecognizerPlay.this.recorder.stop();
                //SpeechRecognizerPlay.this.track.pause();
                SpeechRecognizerPlay.this.decoder.endUtt();
                SpeechRecognizerPlay.this.mainHandler.removeCallbacksAndMessages((Object)null);
                if(this.timeoutSamples != -1 && this.remainingSamples <= 0) {
                    SpeechRecognizerPlay.this.mainHandler.post(SpeechRecognizerPlay.this.new TimeoutEvent());
                }

            }
        }
    }
}
