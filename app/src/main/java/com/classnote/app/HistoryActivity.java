package com.classnote.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史记录：列出每节课的目录，点进去看总结或转写原文。
 */
public class HistoryActivity extends AppCompatActivity {

    private ListView listView;
    private TextView txtEmpty;
    private final List<File> sessions = new ArrayList<File>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        listView = findViewById(R.id.listSessions);
        txtEmpty = findViewById(R.id.txtEmpty);

        adapter = new ArrayAdapter<String>(this, R.layout.item_session, R.id.txtItem,
                new ArrayList<String>());
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= sessions.size()) {
                    return;
                }
                Intent i = new Intent(HistoryActivity.this, DetailActivity.class);
                i.putExtra(DetailActivity.EXTRA_DIR, sessions.get(position).getAbsolutePath());
                startActivity(i);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        sessions.clear();
        sessions.addAll(SessionStore.listSessions(this));

        List<String> labels = new ArrayList<String>();
        for (File dir : sessions) {
            String subject = SessionStore.subjectOf(dir);
            String time = SessionStore.formatTime(SessionStore.startedAt(dir));
            long duration = SessionStore.durationOf(dir);
            boolean hasSummary = new File(dir, "summary.md").exists();
            StringBuilder sb = new StringBuilder();
            sb.append(subject).append("　").append(time);
            if (duration > 0) {
                sb.append("　").append(TextMerge.fmtDuration(duration));
            }
            sb.append(hasSummary ? "　✅ 有笔记" : "　（仅转写）");
            labels.add(sb.toString());
        }

        adapter.clear();
        adapter.addAll(labels);
        adapter.notifyDataSetChanged();

        boolean empty = sessions.isEmpty();
        txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
