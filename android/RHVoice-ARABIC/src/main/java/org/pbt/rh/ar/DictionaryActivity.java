package org.pbt.rh.ar;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class DictionaryActivity extends AppCompatActivity {

    private static final String TAG = "DictionaryActivity";

    private DictionaryManager.UserDict dictionary;
    private EntryAdapter adapter;
    private RecyclerView rvEntries;
    private TextView tvEmpty;
    private String currentSearchQuery = "";
    private List<DictionaryManager.IndexedUserEntry> displayedEntries = new ArrayList<>();

    private TextToSpeech tts;

    private final ActivityResultLauncher<Intent> importLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    handleImportUri(uri);
                }
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary);

        dictionary = new DictionaryManager.UserDict(this);

        tts = new TextToSpeech(this, status -> {});

        MaterialToolbar toolbar = findViewById(R.id.dictionary_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        rvEntries = findViewById(R.id.rv_entries);
        tvEmpty = findViewById(R.id.tv_empty);
        MaterialButton btnAdd = findViewById(R.id.btn_add_entry);

        adapter = new EntryAdapter(this::showEntryOptionsDialog);
        rvEntries.setLayoutManager(new LinearLayoutManager(this));
        rvEntries.setAdapter(adapter);

        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> showAddDialog());
        }

        refreshList();

        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.dictionary_options, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.dict_search_hint));
                searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        currentSearchQuery = query != null ? query.trim() : "";
                        refreshList();
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        currentSearchQuery = newText != null ? newText.trim() : "";
                        refreshList();
                        return true;
                    }
                });
                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        currentSearchQuery = "";
                        refreshList();
                        return true;
                    }
                });
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_export) {
            exportDictionary();
            return true;
        } else if (id == R.id.action_import) {
            openImportPicker();
            return true;
        } else if (id == R.id.action_delete_all) {
            confirmDeleteAll();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportDictionary() {
        File file = dictionary.getFile();
        if (file == null || !file.exists() || file.length() == 0) {
            Toast.makeText(this, R.string.dict_export_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        List<DictionaryManager.UserEntry> all = dictionary.getAll();
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.dict_export_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri contentUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".fileprovider", file);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.dict_export)));
        } catch (Exception e) {
            Log.e(TAG, "Export failed", e);
            Toast.makeText(this, R.string.dict_import_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        // Also accept octet-stream for files that aren't recognized as JSON
        String[] mimeTypes = {"application/json", "application/octet-stream", "text/plain"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        try {
            importLauncher.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Cannot open file picker", e);
        }
    }

    private void handleImportUri(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                speakError();
                return;
            }
            int count = dictionary.importEntries(is);
            if (count < 0) {
                speakError();
            } else {
                refreshList();
                notifyDictionaryChanged();
                Toast.makeText(this, R.string.dict_import_success, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            speakError();
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            handleImportUri(data);
            intent.setAction(null);
            intent.setData(null);
        }
    }

    private void speakError() {
        String errorMsg = getString(R.string.dict_import_fail);
        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
        if (tts != null) {
            tts.speak(errorMsg, TextToSpeech.QUEUE_FLUSH, null, "dict_import_error");
        }
    }

    private void confirmDeleteAll() {
        List<DictionaryManager.UserEntry> all = dictionary.getAll();
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.dict_export_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dict_delete_all)
            .setMessage(R.string.dict_delete_all_confirm)
            .setPositiveButton(R.string.dict_delete, (dialog, which) -> {
                dictionary.clearAll();
                refreshList();
                notifyDictionaryChanged();
                Toast.makeText(this, R.string.dict_cleared, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.dict_cancel, null)
            .show();
    }

    private void refreshList() {
        displayedEntries = dictionary.search(currentSearchQuery != null ? currentSearchQuery : "");
        adapter.submitList(displayedEntries);
        if (displayedEntries.isEmpty()) {
            rvEntries.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            rvEntries.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void notifyDictionaryChanged() {
        try {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(RHVoiceService.ACTION_CONFIG_CHANGE));
        } catch (Exception ignored) {}
    }

    private void showAddDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_dictionary_entry, null);
        TextInputEditText etOriginal = dialogView.findViewById(R.id.et_original);
        TextInputEditText etReplacement = dialogView.findViewById(R.id.et_replacement);

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dict_add)
            .setView(dialogView)
            .setPositiveButton(R.string.dict_save, (dialog, which) -> {
                String orig = etOriginal.getText() != null ? etOriginal.getText().toString().trim() : "";
                String repl = etReplacement.getText() != null ? etReplacement.getText().toString().trim() : "";
                if (!orig.isEmpty() && !repl.isEmpty()) {
                    dictionary.add(orig, repl);
                    refreshList();
                    notifyDictionaryChanged();
                } else {
                    Toast.makeText(this, "يرجى ملء كلا الحقلين", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton(R.string.dict_cancel, null)
            .show();
    }

    private void showEditDialog(int realIndex, DictionaryManager.UserEntry entry) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_dictionary_entry, null);
        TextInputEditText etOriginal = dialogView.findViewById(R.id.et_original);
        TextInputEditText etReplacement = dialogView.findViewById(R.id.et_replacement);

        etOriginal.setText(entry.getOriginal());
        etReplacement.setText(entry.getReplacement());

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dict_edit)
            .setView(dialogView)
            .setPositiveButton(R.string.dict_save, (dialog, which) -> {
                String orig = etOriginal.getText() != null ? etOriginal.getText().toString().trim() : "";
                String repl = etReplacement.getText() != null ? etReplacement.getText().toString().trim() : "";
                if (!orig.isEmpty() && !repl.isEmpty()) {
                    dictionary.update(realIndex, orig, repl);
                    refreshList();
                    notifyDictionaryChanged();
                }
            })
            .setNegativeButton(R.string.dict_cancel, null)
            .show();
    }

    private void showEntryOptionsDialog(int displayPosition) {
        if (displayPosition < 0 || displayPosition >= displayedEntries.size()) return;
        DictionaryManager.IndexedUserEntry indexed = displayedEntries.get(displayPosition);
        int realIndex = indexed.getIndex();
        DictionaryManager.UserEntry entry = indexed.getEntry();

        CharSequence[] options = new CharSequence[]{
            getString(R.string.dict_edit),
            getString(R.string.dict_delete)
        };

        new MaterialAlertDialogBuilder(this)
            .setTitle(entry.getOriginal())
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    showEditDialog(realIndex, entry);
                } else if (which == 1) {
                    dictionary.delete(realIndex);
                    refreshList();
                    notifyDictionaryChanged();
                }
            })
            .setNegativeButton(R.string.dict_cancel, null)
            .show();
    }

    private interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }

    private static class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.VH> {
        private final OnItemLongClickListener listener;
        private List<DictionaryManager.IndexedUserEntry> items = new ArrayList<>();

        public EntryAdapter(OnItemLongClickListener listener) {
            this.listener = listener;
        }

        public void submitList(List<DictionaryManager.IndexedUserEntry> newItems) {
            this.items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dictionary_entry, parent, false);
            return new VH(view, listener);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DictionaryManager.UserEntry entry = items.get(position).getEntry();
            holder.tvOriginal.setText(entry.getOriginal());
            holder.tvReplacement.setText("← " + entry.getReplacement());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvOriginal;
            final TextView tvReplacement;

            VH(View view, OnItemLongClickListener listener) {
                super(view);
                tvOriginal = view.findViewById(R.id.tv_original);
                tvReplacement = view.findViewById(R.id.tv_replacement);
                view.setOnLongClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && listener != null) {
                        listener.onItemLongClick(pos);
                        return true;
                    }
                    return false;
                });
                view.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && listener != null) {
                        listener.onItemLongClick(pos);
                    }
                });
            }
        }
    }
}
