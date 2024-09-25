package com.example.sendimagepractice;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.READ_MEDIA_IMAGES;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.sendimagepractice.databinding.ActivityMainBinding;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ActivityMainBinding viewBinding;
    private FirebaseStorage firebaseStorage;
    private final String STORAGE_ADDRESS = "gs://send-image-pratice.appspot.com";
    private final String STORAGE_FOLDER = "Images";
    private StorageReference storageRef;
    private ExecutorService executor = Executors.newFixedThreadPool(2);

    private ArrayList<Uri> uriArrayList = new ArrayList<>();
    private ArrayList<Bitmap> takenBitmapList = new ArrayList<>();
    private ArrayList<Bitmap> downloadedBitmapList = new ArrayList<>();
    private BitmapListAdapter adapter1;
    private BitmapListAdapter adapter2;

    private Callback callback = new Callback() {
        @Override
        public void onSuccess(String imgLink) {
            Bitmap bitmap = null;
            try {
                Future<Bitmap> future = executor.submit(() -> ImageDownloader.downloadImage(imgLink));
                bitmap = future.get();
            } catch (Exception e) {
                Log.e(TAG, "Error loading image", e);
            }
            downloadedBitmapList.add(bitmap);
            adapter2.notifyDataSetChanged();
            //viewBinding.imageView1.setImageBitmap(bitmap);
        }

        @Override
        public void onError(String error) {

        }
    };

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    accessImageLibraryToPickMulti();
                }
            }
    );

    private final ActivityResultLauncher<Intent> openFileLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK){
                Intent data = result.getData();

                if (data != null) {
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            Uri imageUri = data.getClipData().getItemAt(i).getUri();
                            uriArrayList.add(imageUri);
                        }
                        takeAndDisplayImageOnClipBoard(uriArrayList);
                    }
                }
            }
        }
    );

    ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMedia =
            registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(10), uris -> {
                // Callback is invoked after the user selects media items or closes the
                // photo picker.
                if (!uris.isEmpty()) {
                    Log.d("PhotoPicker", "Number of items selected: " + uris.size());

                    for (Uri link : uris) {
                        uriArrayList.add(link);
                    }

                    takeAndDisplayImageOnClipBoard(uriArrayList);
                } else {
                    Log.d("PhotoPicker", "No media selected");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.getRoot(), (v, insets) -> {
            Insets insets1 = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            viewBinding.getRoot().setPadding(insets1.left, insets1.top, insets1.right, insets1.bottom);
            return insets;
        });
        initSetting();

        storageRef = firebaseStorage.getReference();
    }

    private void initSetting() {
        viewBinding.openLib.setOnClickListener(v -> accessImageLibraryToPickMulti());
        viewBinding.sendImg.setOnClickListener(v -> onSendImage());

        firebaseStorage = FirebaseStorage.getInstance(STORAGE_ADDRESS);
        storageRef = firebaseStorage.getReference();

        takenBitmapList = new ArrayList<>();
        downloadedBitmapList = new ArrayList<>();

        LinearLayoutManager layoutManager1 = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);
        LinearLayoutManager layoutManager2 = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);

        viewBinding.rcView1.setHorizontalScrollBarEnabled(true);
        viewBinding.rcView2.setHorizontalScrollBarEnabled(true);

        viewBinding.rcView1.setLayoutManager(layoutManager1);
        viewBinding.rcView2.setLayoutManager(layoutManager2);

        adapter1 = new BitmapListAdapter(this, takenBitmapList);
        adapter2 = new BitmapListAdapter(this, downloadedBitmapList);

        viewBinding.rcView1.setAdapter(adapter1);
        viewBinding.rcView2.setAdapter(adapter2);
    }

    private void onSendImage() {
        if (takenBitmapList != null){
            uploadImageToFirebase(callback);
        } else {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadImageToFirebase(Callback callback) {
        if (takenBitmapList != null) {
            for (Uri imageUri : uriArrayList) {
                StorageReference imageRef = storageRef.child(STORAGE_FOLDER).child(Objects.requireNonNull(imageUri.getLastPathSegment()));
                UploadTask uploadTask = imageRef.putFile(imageUri);

                uploadTask.addOnSuccessListener(taskSnapshot -> {
                    Toast.makeText(MainActivity.this, "Successes to upload image", Toast.LENGTH_SHORT).show();

                    imageRef.getDownloadUrl().addOnSuccessListener(uri -> callback.onSuccess(uri.toString()));
                }).addOnFailureListener(exception -> {
                    // Task failed with an exception
                    Toast.makeText(MainActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "uploadImageToFirebase: ", exception);
                });
            }
        } else {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void accessImageLibraryToPickMulti(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkAndRequestPermission(READ_MEDIA_IMAGES, this::launchImagePicker);
        } else {
            checkAndRequestPermission(READ_EXTERNAL_STORAGE, this::launchImagePicker);
        }
    }

    private void checkAndRequestPermission(String permission, Runnable onGranted) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            onGranted.run();
        } else {
            if (shouldShowRequestPermissionRationale(permission)) {
                showExplanationDialog();
            } else {
                Toast.makeText(this, "Media access permission not granted.", Toast.LENGTH_SHORT).show();
                requestPermissionLauncher.launch(permission);
            }
        }
    }

    private void launchImagePicker() {
        pickMultipleMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                .build());
    }

    private void showExplanationDialog() {
        new AlertDialog.Builder(this).setTitle("Permission Needed")
                .setMessage("This app needs to access your media files to display images.")
                .setPositiveButton("Go to setting", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri =  Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create().show();
    }

    private void takeAndDisplayImageOnClipBoard(ArrayList<Uri> uriArrayList) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU){
            if (uriArrayList == null) {
                Toast.makeText(this, "Uri is null", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this,"uriList size: " + uriArrayList.size(), Toast.LENGTH_SHORT).show();
            DocumentFile file;

            takenBitmapList.clear();

            adapter1.notifyDataSetChanged();

            for (int i = 0; i < uriArrayList.size(); i++) {
                Uri uri = uriArrayList.get(i);
                file = DocumentFile.fromSingleUri(this, uri);
                if (file != null && file.exists()){

                    try (InputStream stream = getContentResolver().openInputStream(file.getUri())) {
                        Bitmap bitmap = BitmapFactory.decodeStream(stream);

                        takenBitmapList.add(bitmap);
                        adapter1.notifyDataSetChanged();
                    } catch (IOException e) {
                        // Handle the exception...
                    }

                } else Log.d("onDisPlay", "displayImageFromStorage: file not found");
            }
            //uriArrayList.clear();
        }
    }
}