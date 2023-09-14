package com.example.streamingvoicetotext;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechSettings;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvResult;
    private Button btnStart;

    private VoiceRecorder mVoiceRecorder;
    private Thread recordingThread;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    private final VoiceRecorder.Callback mVoiceCallback = new VoiceRecorder.Callback() {

        @Override
        public void onVoiceStart() {

        }

        @Override
        public void onVoice(byte[] data, int size) {
            try {

                request =
                        StreamingRecognizeRequest.newBuilder()
                                .setAudioContent(ByteString.copyFrom(data))
                                .build();
                clientStream.send(request);
                responseObserver.onComplete();

            }catch (Exception e){
                Log.e("kya",""+e.getMessage());
            }
        }

        @Override
        public void onVoiceEnd() {

            transcribeRecording();

        }

    };
    private SpeechClient speechClient;
    private Thread transcribeThread;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvResult = findViewById(R.id.tvResult);
        btnStart = findViewById(R.id.btnStart);

        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (permissionToRecordAccepted) {
                    if (btnStart.getText().toString().equals("Start")){
                        btnStart.setText("Stop");
                        startVoiceRecorder();
                        isRecording = false;
                        try {
                            createRecognizeRequestFromVoice();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

            }
        });

        // Initialize the SpeechClient
        initializeSpeechClient();
    }

    private void startVoiceRecorder() {
        recordingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (mVoiceRecorder != null) {
                    mVoiceRecorder.stop();
                }
                mVoiceRecorder = new VoiceRecorder(mVoiceCallback);
                mVoiceRecorder.start();
            }
        });
        recordingThread.start();
    }
    private void stopVoiceRecorder() {

        if (mVoiceRecorder != null) {
            mVoiceRecorder.stop();
            mVoiceRecorder = null;
        }
        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            recordingThread = null;
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        if (!permissionToRecordAccepted) {

            tvResult.setText("Permission Denied");
        }
    }

    private void initializeSpeechClient() {
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(getResources().openRawResource(R.raw.credentials));
            FixedCredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
            speechClient = SpeechClient.create(SpeechSettings.newBuilder().setCredentialsProvider(credentialsProvider).build());
        } catch (IOException e) {

            Log.e("kya", "InitException" + e.getMessage());
        }
    }
    private void transcribeRecording() {
        transcribeThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    createRecognizeRequestFromVoice();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        });
        transcribeThread.start();
    }
    private ResponseObserver<StreamingRecognizeResponse> responseObserver = null;
    private String lastProcessedTranscript = "";
    private boolean isRecording = false;
    private ClientStream<StreamingRecognizeRequest> clientStream;
    private StreamingRecognizeRequest request;
    private void createRecognizeRequestFromVoice() throws IOException {

        responseObserver =
                new ResponseObserver<StreamingRecognizeResponse>() {
                    List<StreamingRecognizeResponse> responses = new ArrayList<>();

                    public void onStart(StreamController controller) {
                        Log.e("kya", "-+-> ");
                    }

                    public void onResponse(StreamingRecognizeResponse response) {
                        //Log.e("kya","-+-> "+response.getResultsCount());
                        responses.add(response);
                    }

                    public void onComplete() {
                        for (StreamingRecognizeResponse response : responses) {
                            if (response.getResultsList().size() > 0) {
                                StreamingRecognitionResult result = response.getResultsList().get(response.getResultsList().size() - 1);
                                //SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(response.getResultsList().size() - 1);
                                String transcript = alternative.getTranscript();
                                if (!transcript.equals(lastProcessedTranscript)) {
                                    // Log the transcript
                                    Log.e("kya", "--> " + transcript);

                                    // Perform any desired actions with the recognized transcript
                                    //stringBuilder.append(transcript).append(" ");
                                    if (!isRecording)
                                        updateResult(transcript);

                                    // Update the last processed transcript
                                    lastProcessedTranscript = transcript;
                                }
                            }
                        }

                    }

                    public void onError(Throwable t) {
                        System.out.println(t);
                    }
                };
        clientStream =
                speechClient.streamingRecognizeCallable().splitCall(responseObserver);
        RecognitionConfig recognitionConfig =
                RecognitionConfig.newBuilder()
                        .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                        .setLanguageCode("en-US")
                        .setSampleRateHertz(16000)
                        .build();
        StreamingRecognitionConfig streamingRecognitionConfig =
                StreamingRecognitionConfig.newBuilder().setConfig(recognitionConfig).build();
        request =
                StreamingRecognizeRequest.newBuilder()
                        .setStreamingConfig(streamingRecognitionConfig)
                        .build();
        clientStream.send(request);
        responseObserver.onComplete();

    }
    private void updateResult(final String transcript) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                playSound();
                tvResult.setText(transcript);
                btnStart.setText("Start");
                stopVoiceRecorder();
            }
        });
    }
    private MediaPlayer mediaPlayer;
    private void playSound(){
        mediaPlayer = MediaPlayer.create(this, R.raw.transcribe_voice);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mediaPlayer.release();
            }
        });
        mediaPlayer.start();
    }
}