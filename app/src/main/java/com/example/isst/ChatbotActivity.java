package com.example.isst;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ChatbotActivity extends AppCompatActivity {

    private static final String TAG = "GEMINI_CHAT";

    // 🔥 PUT YOUR GEMINI API KEY HERE
    private String GEMINI_KEY() {
        return SpaceApiConfig.GEMINI_KEY;
    }

    TextView textChatHistory;
    EditText editQuestion;
    ScrollView scrollChat;
    ProgressBar progressBar;

    OkHttpClient client;

    StringBuilder chatHistory = new StringBuilder(
            "🌌 StarTrack AI\n" +
                    "Ask me anything about the ISS, planets, or space!\n\n"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        // Initialize HTTP client
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        // Initialize views
        textChatHistory = findViewById(R.id.textChatHistory);
        editQuestion = findViewById(R.id.editQuestion);
        scrollChat = findViewById(R.id.scrollChat);
        progressBar = findViewById(R.id.progressBar);

        textChatHistory.setText(chatHistory.toString());

        // Quick question buttons
        findViewById(R.id.btnQ1).setOnClickListener(v ->
                askGemini("How fast does the ISS travel?")
        );

        findViewById(R.id.btnQ2).setOnClickListener(v ->
                askGemini("How far is the ISS from Earth?")
        );

        findViewById(R.id.btnQ3).setOnClickListener(v ->
                askGemini("What is a satellite and how does it stay in orbit?")
        );

        findViewById(R.id.btnQ4).setOnClickListener(v ->
                askGemini("Why does the ISS orbit Earth and not fall down?")
        );

        // Ask button
        findViewById(R.id.btnAsk).setOnClickListener(v ->
                sendFromInput()
        );

        // Keyboard send action
        editQuestion.setOnEditorActionListener((v, actionId, event) -> {

            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInput();
                return true;
            }

            return false;
        });

        // Back button
        findViewById(R.id.btnBackFromChat).setOnClickListener(v ->
                finish()
        );
    }

    // =========================================
    // SEND USER INPUT
    // =========================================

    private void sendFromInput() {

        String q = editQuestion.getText().toString().trim();

        if (!q.isEmpty()) {

            editQuestion.setText("");

            askGemini(q);
        }
    }

    // =========================================
    // GEMINI REQUEST
    // =========================================

    private void askGemini(String question) {

        if (!SpaceApiConfig.isGeminiConfigured()) {

            replaceThinking(
                    "Gemini API key not configured.\n\n" +
                            "Open SpaceApiConfig.java and paste your Gemini API key."
            );

            return;
        }

        // Add user message
        chatHistory.append("👤 You: ")
                .append(question)
                .append("\n\n");

        chatHistory.append("🤖 AI: Thinking...\n\n");

        runOnUiThread(() -> {

            textChatHistory.setText(chatHistory.toString());

            progressBar.setVisibility(View.VISIBLE);

            scrollToBottom();
        });

        try {

            // ==========================
            // BUILD GEMINI JSON
            // ==========================

            JSONObject textPart = new JSONObject();
            textPart.put("text", question);

            JSONArray partsArray = new JSONArray();
            partsArray.put(textPart);

            JSONObject contentObject = new JSONObject();
            contentObject.put("parts", partsArray);

            JSONArray contentsArray = new JSONArray();
            contentsArray.put(contentObject);

            JSONObject body = new JSONObject();
            body.put("contents", contentsArray);

            // ==========================
            // REQUEST BODY
            // ==========================

            RequestBody requestBody = RequestBody.create(
                    body.toString(),
                    MediaType.get("application/json")
            );

            // ==========================
            // GEMINI URL
            // ==========================

            String url =
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                            "gemini-2.5-flash:generateContent?key=" + GEMINI_KEY();

            // ==========================
            // HTTP REQUEST
            // ==========================

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build();

            // ==========================
            // SEND REQUEST
            // ==========================

            client.newCall(request).enqueue(new Callback() {

                @Override
                public void onFailure(Call call, IOException e) {

                    Log.e(TAG, "NETWORK ERROR: " + e.getMessage(), e);

                    replaceThinking(
                            "Network error.\n\n" +
                                    e.getMessage()
                    );
                }

                @Override
                public void onResponse(Call call, Response response)
                        throws IOException {

                    String rawBody =
                            response.body() != null
                                    ? response.body().string()
                                    : "";

                    Log.d(TAG, "HTTP " + response.code());

                    Log.d(TAG, rawBody);

                    try {

                        if (!response.isSuccessful()) {

                            String errorMessage;

                            switch (response.code()) {

                                case 400:
                                    errorMessage = "Invalid Gemini request.";
                                    break;

                                case 401:
                                    errorMessage = "Invalid Gemini API key.";
                                    break;

                                case 403:
                                    errorMessage = "Gemini API access denied.";
                                    break;

                                case 429:
                                    errorMessage = "Gemini quota exceeded.";
                                    break;

                                case 500:
                                    errorMessage = "Gemini server error.";
                                    break;

                                default:
                                    errorMessage = "API Error: " + response.code();
                            }

                            replaceThinking(errorMessage);

                            return;
                        }

                        // ==========================
                        // PARSE GEMINI RESPONSE
                        // ==========================

                        JSONObject obj =
                                new JSONObject(rawBody);

                        String answer = obj
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");

                        replaceThinking(answer);

                    } catch (Exception e) {

                        Log.e(TAG, "PARSE ERROR: " + e.getMessage(), e);

                        replaceThinking(
                                "Parse error.\n\n" +
                                        e.getMessage()
                        );

                    } finally {

                        response.close();
                    }
                }
            });

        } catch (Exception e) {

            Log.e(TAG, "REQUEST ERROR: " + e.getMessage(), e);

            replaceThinking(
                    "Request failed.\n\n" +
                            e.getMessage()
            );
        }
    }

    // =========================================
    // REPLACE THINKING MESSAGE
    // =========================================

    private void replaceThinking(String answer) {

        String updated = chatHistory.toString().replace(
                "🤖 AI: Thinking...\n\n",
                "🤖 AI: " + answer + "\n\n"
        );

        chatHistory = new StringBuilder(updated);

        runOnUiThread(() -> {

            progressBar.setVisibility(View.GONE);

            textChatHistory.setText(chatHistory.toString());

            scrollToBottom();
        });
    }

    // =========================================
    // AUTO SCROLL
    // =========================================

    private void scrollToBottom() {

        scrollChat.post(() ->
                scrollChat.fullScroll(ScrollView.FOCUS_DOWN)
        );
    }
}