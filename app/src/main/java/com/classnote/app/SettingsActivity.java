package com.classnote.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 设置页：两个服务（转写 / 总结）的地址、Key、模型名，以及分片时长。
 */
public class SettingsActivity extends AppCompatActivity {

    private EditText edAsrBase;
    private EditText edAsrKey;
    private EditText edAsrModel;
    private EditText edChunk;
    private EditText edLlmBase;
    private EditText edLlmKey;
    private EditText edLlmModel;

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        edAsrBase = findViewById(R.id.edAsrBase);
        edAsrKey = findViewById(R.id.edAsrKey);
        edAsrModel = findViewById(R.id.edAsrModel);
        edChunk = findViewById(R.id.edChunk);
        edLlmBase = findViewById(R.id.edLlmBase);
        edLlmKey = findViewById(R.id.edLlmKey);
        edLlmModel = findViewById(R.id.edLlmModel);

        Button btnSave = findViewById(R.id.btnSave);
        Button btnTest = findViewById(R.id.btnTest);
        Button btnDefault = findViewById(R.id.btnDefault);

        loadValues();

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveValues();
                Toast.makeText(SettingsActivity.this, "已保存", Toast.LENGTH_SHORT).show();
            }
        });

        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveValues();
                testLlm();
            }
        });

        btnDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edAsrBase.setText(Prefs.DEFAULT_ASR_BASE);
                edAsrModel.setText(Prefs.DEFAULT_ASR_MODEL);
                edLlmBase.setText(Prefs.DEFAULT_LLM_BASE);
                edLlmModel.setText(Prefs.DEFAULT_LLM_MODEL);
                Toast.makeText(SettingsActivity.this, "已填入默认值，记得点“保存设置”",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadValues() {
        edAsrBase.setText(Prefs.asrBase(this));
        edAsrKey.setText(Prefs.asrKey(this));
        edAsrModel.setText(Prefs.asrModel(this));
        edChunk.setText(String.valueOf(Prefs.chunkSeconds(this)));
        edLlmBase.setText(Prefs.llmBase(this));
        edLlmKey.setText(Prefs.llmKey(this));
        edLlmModel.setText(Prefs.llmModel(this));
    }

    private void saveValues() {
        Prefs.put(this, "asr_base", textOf(edAsrBase));
        Prefs.put(this, "asr_key", textOf(edAsrKey));
        Prefs.put(this, "asr_model", textOf(edAsrModel));
        Prefs.put(this, "chunk_seconds", textOf(edChunk));
        Prefs.put(this, "llm_base", textOf(edLlmBase));
        Prefs.put(this, "llm_key", textOf(edLlmKey));
        Prefs.put(this, "llm_model", textOf(edLlmModel));
        setResult(RESULT_OK);
    }

    private String textOf(EditText e) {
        if (e == null || e.getText() == null) {
            return "";
        }
        return e.getText().toString().trim();
    }

    private void testLlm() {
        final Button btnTest = findViewById(R.id.btnTest);
        btnTest.setEnabled(false);
        Toast.makeText(this, "正在测试总结服务…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String result;
                try {
                    String reply = LlmClient.test(SettingsActivity.this);
                    result = "连接成功，模型回复：" + reply;
                } catch (Exception e) {
                    result = "连接失败：" + e.getMessage();
                }
                final String finalResult = result;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        btnTest.setEnabled(true);
                        Toast.makeText(SettingsActivity.this, finalResult, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "classnote-test").start();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
