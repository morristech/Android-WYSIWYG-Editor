/*
 * Copyright (C) 2016 Muhammed Irshad
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.irshu.libs.Components;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.irshu.libs.BaseClass;
import com.irshu.editor.R;
import com.irshu.libs.models.EditorControl;
import com.irshu.libs.models.EditorState;
import com.irshu.libs.models.EditorType;
import com.irshu.libs.models.IEditorRetrofitApi;
import com.irshu.libs.models.ImageResponse;
import com.irshu.libs.models.ServiceGenerator;
import com.squareup.picasso.Picasso;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.TimerTask;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;

/**
 * Created by mkallingal on 5/1/2016.
 */
public class ImageExtensions {
    private Context _Context;
    private BaseClass base;
    public ImageExtensions(BaseClass baseClass){
        this._Context= baseClass.context;
        this.base = baseClass;
    }

    public void OpenImageGallery() {
        int Index=this.base.determineIndex(EditorType.none);
        EditorState state= base.GetState();
        state.PendingIndex= Index;
        Intent intent = new Intent();
// Show only images, no videos or anything else
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
    // Always show the chooser (if there are multiple options available)
        ((Activity)_Context).startActivityForResult(Intent.createChooser(intent, "Select an image"), base.PICK_IMAGE_REQUEST);
    }

    public void InsertImage(Bitmap _image) {
       // RenderEditor(getStateFromString());
        final View childLayout = ((Activity) _Context).getLayoutInflater().inflate(R.layout.editor_image_view, null);
        ImageView imageView = (ImageView) childLayout.findViewById(R.id.imageView);
        imageView.setImageBitmap(_image);
        final String uuid= this.base.objEngine.GenerateUUID();
        BindEvents(childLayout);
        int Index= base.determineIndex(EditorType.img);
        base.parentView.addView(childLayout, Index);
        //      _Views.add(childLayout);
        if(base.isLastRow(childLayout)) {
            base.inputExtensions.InsertEditText(Index + 1, "", "");
        }
        UploadImageToServer(_image, childLayout, uuid);
    }

    /*
      /used by the renderer to render the image from the state
    */
    public  void loadImage(String _path, ImageView.ScaleType scaleType){
        ImageView imageView = new ImageView(base.context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imageView.setLayoutParams(params);
        imageView.setScaleType(scaleType);
        Picasso.with(base.context).load(_path).into(imageView);
        base.parentView.addView(imageView);
    }
    /*
      /used to fetch an image from internet and return a Bitmap equivalent
    */
    public static Bitmap getBitmapFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            // Log exception
            return null;
        }
    }

    /*
         /used to upload the image to remote
       */
    private  void UploadImageToServer(Bitmap bitmap, final View view, String uuid){
        File f = new File(_Context.getCacheDir(), uuid+".png");
        try {
            f.createNewFile();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(bitmapdata);
            fos.flush();
            fos.close();


            IEditorRetrofitApi service =
                    ServiceGenerator.createService(IEditorRetrofitApi.class);
            RequestBody requestFile =
                    RequestBody.create(MediaType.parse("multipart/form-data"), f);

            // MultipartBody.Part is used to send also the actual file name
            MultipartBody.Part body =
                    MultipartBody.Part.createFormData("picture", f.getName(), requestFile);

            // add another part within the multipart request
            RequestBody description =
                    RequestBody.create(
                            MediaType.parse("multipart/form-data"), "");
            // finally, execute the request
            Call<ImageResponse> call = service.upload(base.ImageUploaderUri, description, body);
            call.enqueue(new Callback<ImageResponse>() {
                @Override
                public void onResponse(Call<ImageResponse> call, final retrofit2.Response<ImageResponse> response) {
                    ((TextView) view.findViewById(R.id.lblStatus)).setText("Uploaded");
                    EditorControl control= base.CreateTag(EditorType.img);
                    control.Path= response.body().Uri;
                    control.scaleType= ImageView.ScaleType.CENTER_CROP;
                    view.setTag(control);
                    new java.util.Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            ((Activity)base.context).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // This code will always run on the UI thread, therefore is safe to modify UI elements.
                                    view.findViewById(R.id.lblStatus).setVisibility(View.GONE);
                                }
                            });
                        }
                    },3000);
                    view.findViewById(R.id.progress).setVisibility(View.GONE);
                }

                @Override
                public void onFailure(Call<ImageResponse> call, Throwable t) {
                    ((TextView) view.findViewById(R.id.lblStatus)).setText(t.getMessage());
                    view.findViewById(R.id.progress).setVisibility(View.GONE);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void SetImageScaleType(View view, ImageView.ScaleType scaleType){
        final ImageView imageView= (ImageView) view.findViewById(R.id.imageView);
        imageView.setScaleType(scaleType);
        EditorControl tag= base.GetControlTag(view);
        tag.scaleType= scaleType;
        view.setTag(tag);
    }

    private void BindEvents(final View layout){
        final ImageView imageView= (ImageView) layout.findViewById(R.id.imageView);
        final View btnFitWidth=layout.findViewById(R.id.btnFitWidth);
        final View btnCenterCrop=layout.findViewById(R.id.btnCenterCrop);
        final View btn_remove=layout.findViewById(R.id.btn_remove);
        final View btnStretch=layout.findViewById(R.id.btnStretch);
        btnFitWidth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SetImageScaleType(layout,ImageView.ScaleType.FIT_END);
            }
        });
        btnCenterCrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SetImageScaleType(layout, ImageView.ScaleType.CENTER_CROP);
            }
        });
        btn_remove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                base.parentView.removeView(layout);
            }
        });
        btnStretch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SetImageScaleType(layout, ImageView.ScaleType.FIT_XY);
            }
        });
        imageView.setOnTouchListener(new View.OnTouchListener() {
            private Rect rect;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    imageView.setColorFilter(Color.argb(50, 0, 0, 0));
                    rect = new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    imageView.setColorFilter(Color.argb(0, 0, 0, 0));
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!rect.contains(v.getLeft() + (int) event.getX(), v.getTop() + (int) event.getY())) {
                        imageView.setColorFilter(Color.argb(0, 0, 0, 0));
                    }
                }
                return false;
            }
        });
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    btn_remove.setVisibility(View.VISIBLE);
                    btnCenterCrop.setVisibility(View.VISIBLE);
                    btnFitWidth.setVisibility(View.VISIBLE);
                    btnStretch.setVisibility(View.VISIBLE);
                }
            });
            imageView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    btn_remove.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
                    btnCenterCrop.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
                    btnFitWidth.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
                    btnStretch.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
                }
            });
    }
}