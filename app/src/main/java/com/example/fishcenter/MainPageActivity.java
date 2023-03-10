package com.example.fishcenter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class MainPageActivity extends AppCompatActivity implements OnClickListener {

    private ImageButton fishRecognitionImageButton;
    private ImageButton googleMapsButton;
    private ImageButton logoutImageButton;
    private ImageButton reloadPostsButton;
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private final FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private ArrayList<PostModel> posts = new ArrayList<>();
    private RecyclerView postsRecyclerView;
    private PostRecyclerViewAdapter adapter;

    private LinearLayout linearLayoutIndeterminateProgressBar;
    private LinearLayout linearLayoutIndeterminateProgressBarToMaps;
    private LinearLayout linearLayoutNoPostsToLoad;
    private String currentUserId = FirebaseAuth.getInstance().getUid();
    private byte[] userProfilePic;
    private String userNickname;
    private int postsCount = -1;
    private boolean postDataFetched = false;
    private boolean userProfilePictureFetched = false;
    private boolean userNicknamePictureFetched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mainpage);
        // https://developer.android.com/develop/ui/views/components/appbar/setting-up
        // need to find custom defined toolbar in the xml and replace the vanilla toolbar with it
        FloatingActionButton floatingActionButton = findViewById(R.id.floatingActionButton);
        fishRecognitionImageButton = findViewById(R.id.fishRecognitionImageButton);
        googleMapsButton = findViewById(R.id.googleMapsButton);
        logoutImageButton = findViewById(R.id.logoutImageButton);
        reloadPostsButton = findViewById(R.id.reloadPostsButton);
        postsRecyclerView = findViewById(R.id.postsRecyclerView);
        linearLayoutNoPostsToLoad = findViewById(R.id.linearLayoutNoPostsToLoad);
        linearLayoutIndeterminateProgressBar = findViewById(R.id.linearLayoutIndeterminateProgressBar);
        linearLayoutIndeterminateProgressBarToMaps = findViewById(R.id.linearLayoutIndeterminateProgressBarToMaps);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        linearLayoutIndeterminateProgressBar.setVisibility(View.VISIBLE);
        getUserProfPicture();
        getUserNickname();
        getPostData();
        waitForPostDataAndSetupRecyclerView();

        floatingActionButton.setOnClickListener(view -> {
            if(postDataFetched && userProfilePictureFetched && userNicknamePictureFetched) {
                Intent createPost = new Intent(getApplicationContext(), CreatePost.class);
                createPost.putExtra("profilePicture", userProfilePic);
                createPost.putExtra("userNickname", userNickname);
                startActivityForResult(createPost, 1);
            }
        });

        logoutImageButton.setOnClickListener(view -> {
            if(postDataFetched && userProfilePictureFetched && userNicknamePictureFetched) {
                firebaseAuth.signOut();
                Intent loginActivity = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(loginActivity);
                finish();
            }
        });

        fishRecognitionImageButton.setOnClickListener(view -> {
            if(postDataFetched && userProfilePictureFetched && userNicknamePictureFetched) {
                Intent fishRecognitionActivity = new Intent(getApplicationContext(), FishRecognitionActivity.class);
                startActivity(fishRecognitionActivity);
            }
        });

        googleMapsButton.setOnClickListener(view -> {
            if(postDataFetched && userProfilePictureFetched && userNicknamePictureFetched) {
                Intent mapActivity = new Intent(getApplicationContext(), MapActivity.class);
                startActivity(mapActivity);
            }
        });

        reloadPostsButton.setOnClickListener(view -> {
            if(postDataFetched && userProfilePictureFetched && userNicknamePictureFetched) {
                linearLayoutIndeterminateProgressBar.setVisibility(View.VISIBLE);
                posts.clear();
                postsCount = -1;
                postDataFetched = false;
                getPostData();
                waitForPostDataAndSetupRecyclerView();
                adapter.notifyDataSetChanged();
            }
        });
    }



    public void setButtonsInteractiveBackgrounds(boolean flag) {
        if(flag) {
            fishRecognitionImageButton.setBackground(getDrawable(R.drawable.background_rounded_corners_toggle_5_gray_opacity_30_to_transparent));
            googleMapsButton.setBackground(getDrawable(R.drawable.background_rounded_corners_toggle_5_gray_opacity_30_to_transparent));
            logoutImageButton.setBackground(getDrawable(R.drawable.background_rounded_corners_toggle_5_gray_opacity_25_to_transparent));
        } else {
            fishRecognitionImageButton.setBackground(null);
            googleMapsButton.setBackground(null);
            logoutImageButton.setBackground(null);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // get data sent back from create post so that the new post can be displayed on the user screen
        if(resultCode == Activity.RESULT_OK && requestCode == 1) {
            // get data passed back from the createPost activity
            LocalPost localPost = (LocalPost) data.getSerializableExtra("localPost");
            PostModel newPost = new PostModel(
                    getApplicationContext(),
                    localPost.getTitle(),
                    localPost.getBody(),
                    userProfilePic,
                    userNickname,
                    localPost.getPostUploadDate(),
                    localPost.getNumLikes(),
                    localPost.getMedia(),
                    localPost.getMimeType(),
                    localPost.getUniquePostRef(),
                    currentUserId,
                    localPost.getPostLikedBy()
            );
            // add this post to posts and re-sort them
            posts.add(newPost);
            Collections.sort(posts, new TimestampComparator());
            // notify the recyclerview adapter of the change in data
            adapter.notifyDataSetChanged();
        }
    }

    // minimise the app when the back button is clicked on the main menu
    @Override
    public void onBackPressed() {
        this.moveTaskToBack(true);
    }



    // handle on-clicks for videos
    @Override
    public void onClickVideoThumbnail(int position) {
        // switch to the play video activity and supply it with the uri of the video that should be displayed
        Intent videoActivity = new Intent(getApplicationContext(), PlayVideoActivity.class);
        String videoUri = posts.get(position).getMedia().toString();
        videoActivity.putExtra("video", videoUri);
        startActivity(videoActivity);
    }

    // handle on-clicks for like buttons

    @Override
    public void onClickLikeButton(int position) {
        // local update
        ArrayList<String> listOfUsers = posts.get(position).getPostLikedBy();
        String currentUser = firebaseAuth.getCurrentUser().getUid();
        if(listOfUsers == null) {
            listOfUsers = new ArrayList<>(Arrays.asList(currentUser));
        } else if (listOfUsers.contains(currentUser)) {
            listOfUsers.remove(currentUser);
        } else  {
            listOfUsers.add(currentUser);
        }
        posts.get(position).setPostLikedBy(listOfUsers);
        posts.get(position).setNumLikes(listOfUsers.size());
        adapter.notifyDataSetChanged();

        // list of users
        String uniquePostRef = posts.get(position).getUniquePostRef();
        Map<String, Object> listOfUsersMap = new HashMap<>();
        listOfUsersMap.put("likedBy", listOfUsers);
        firestore.collection("posts").document(uniquePostRef).update(listOfUsersMap);

        // num likes
        Map<String, Object> numLikesMap = new HashMap<>();
        numLikesMap.put("likes", listOfUsers.size());
        firestore.collection("posts").document(uniquePostRef).update(numLikesMap);
    }




    private void waitForPostDataAndSetupRecyclerView() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                while (!postDataFetched) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (postsCount == 0) {
                                linearLayoutNoPostsToLoad.setVisibility(View.VISIBLE);
                                linearLayoutIndeterminateProgressBar.setVisibility(View.GONE);
                                setButtonsInteractiveBackgrounds(true);
                                postDataFetched = true;
                            }
                            if (postsCount == posts.size()) {
                                // sort the posts based on timestamp, firestore is meant to sort them but it is not consistent
                                // maybe they are fetched in the correct order but the posts are added to the array at different
                                // in a different order since the wait time for some posts in callbacks is shorter than for others
                                // for example, no media for one post and 10 mb video for the other post
                                Collections.sort(posts, new TimestampComparator());
                                //create the adapter with post date and attach it to the recycler view and pass this class as the listener for on click methods in the for the recycle view
                                adapter = new PostRecyclerViewAdapter(getApplicationContext(), posts, MainPageActivity.this);
                                postsRecyclerView.setAdapter(adapter);
                                // wait until the layout has been fully inflated and only then set the flag for data fetches
                                // avoids layout being displayed with some parts no loaded (videos, etc)
                                postsRecyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
                                setButtonsInteractiveBackgrounds(true);
                                postDataFetched = true;
                                linearLayoutIndeterminateProgressBar.setVisibility(View.GONE);
                            }
                        }
                    });
                }
            }
        }.start();
    }

    public void getUserNickname() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                firestore.collection("users").document(currentUserId).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        userNickname = (String) documentSnapshot.get("nickname");
                        userNicknamePictureFetched = true;
                    }
                });
            }
        }.start();
    }

    public void getUserProfPicture() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                firestore.collection("users").document(currentUserId).get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        userNickname = (String) documentSnapshot.get("nickname");
                        userProfilePictureFetched = true;
                    }
                });
            }
        }.start();
    }

    public void getPostData() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                // get all posts from firestore ordering based on timestamp
                firestore.collection("posts").orderBy("timestamp", Query.Direction.ASCENDING).get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        postsCount = queryDocumentSnapshots.size();
                        // get all posts in firestore
                        for (QueryDocumentSnapshot post : queryDocumentSnapshots) {
                            String nickname = (String) post.get("nickname");
                            String title = (String) post.get("title");
                            String body = (String) post.get("body");
                            // extract a readable date from the firebase timestamp
                            Date firestoreTimestamp = ((Timestamp) post.get("timestamp")).toDate();
                            DateFormat dateFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm");
                            String postUploadDate = dateFormatter.format(firestoreTimestamp);
                            String likes = post.get("likes").toString();
                            String userId = post.get("userId").toString();
                            String mimeType = post.get("mimeType").toString();
                            String uniquePostRef = post.getId();
                            ArrayList<String> postLikedBy = (ArrayList<String>) post.get("likedBy");
                            // get media from firestore cloud if exists then create an PostModel object  with media otherwise set media to null
                            StorageReference storageRefMedia = firebaseStorage.getReference().child("/postMedia/" + post.getId());
                            storageRefMedia.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                @Override
                                public void onSuccess(Uri uri) {
                                    // the post had media associated with it and profile picture is fetched below
                                    String uriMedia = String.valueOf(uri);
                                    // add on the media, profile picture and metadata along with standard post components
                                    PostModel post = new PostModel(getApplicationContext(), title, body, userProfilePic, nickname, postUploadDate, likes, uriMedia, mimeType, uniquePostRef, userId, postLikedBy);
                                    posts.add(post);
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    // the post did not have any media associated with it and profile picture is fetched below
                                    PostModel post = new PostModel(getApplicationContext(), title, body, userProfilePic, nickname, postUploadDate, likes, null, null, uniquePostRef, userId, postLikedBy);
                                    posts.add(post);
                                }
                            });
                        }
                    }
                });
            }
        }.start();
    }
}

