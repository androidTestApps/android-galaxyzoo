/*
 * Copyright (C) 2014 Murray Cumming
 *
 * This file is part of android-galaxyzoo
 *
 * android-galaxyzoo is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * android-galaxyzoo is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with android-galaxyzoo.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.murrayc.galaxyzoo.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * A simple {@link android.app.Fragment} subclass.
 */
public class ExampleViewerFragment extends Fragment {
    public static final String ARG_EXAMPLE_ICON_NAME = "example-icon-name";
    private View mLoadingView;
    private View mRootView;
    private String mExampleIconName = null;

    public ExampleViewerFragment() {
        // Required empty public constructor
    }

    /**
     * We need to load the bitmap for the imageview in an async task.
     * This is tedious. It would be far easier if ImageView had a setFromUrl(url) method that did
     * the work asynchronously itself.
     *
     * @param strIconName
     * @param imageView
     */
    private void loadBitmap(final String strIconName, ImageView imageView) {
        showLoadingView(true);
        final BitmapWorkerTask task = new BitmapWorkerTask(imageView, this);
        task.execute(strIconName);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        final Bundle bundle = getArguments();
        if (bundle != null) {
            setExampleIconName(bundle.getString(ARG_EXAMPLE_ICON_NAME));
        }

        mRootView = inflater.inflate(R.layout.fragment_example_viewer, container, false);

        update();

        return mRootView;
    }

    private static final String ASSET_PATH_EXAMPLES_DIR = "examples/";

    public void update() {
        final ImageView imageView = (ImageView) mRootView.findViewById(R.id.imageView);
        if (imageView != null) {
            loadBitmap(mExampleIconName, imageView);
        }
    }

    public void setExampleIconName(final String exampleIconName) {
        mExampleIconName = exampleIconName;
    }

    //See http://developer.android.com/training/displaying-bitmaps/process-bitmap.html
    private static class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private final WeakReference<ExampleViewerFragment> fragmentReference;

        private String strName = null;

        public BitmapWorkerTask(final ImageView imageView, final ExampleViewerFragment fragment) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            imageViewReference = new WeakReference<>(imageView);

            // Use a WeakReference to ensure the ImageView can be garbage collected
            fragment.showLoadingView(true);
            fragmentReference = new WeakReference<>(fragment);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {
            strName = params[0];

            Context context = null;
            if (fragmentReference != null) {
                final ExampleViewerFragment fragment = fragmentReference.get();
                if (fragment != null) {
                    context = fragment.getActivity();
                }
            }

            if (context == null) {
                Log.error("BitmapWorkerTask.doInBackground(): context was null.");
                return null;
            }

            final String assetPath = ASSET_PATH_EXAMPLES_DIR + strName + ".jpg";

            Bitmap bitmap = null;
            final InputStream inputStreamAsset = Utils.openAsset(context, assetPath);
            if(inputStreamAsset != null) {
                bitmap = BitmapFactory.decodeStream(inputStreamAsset);
            }

            return bitmap;
        }

        // Once complete, see if ImageView is still around and set bitmap.
        // This avoids calling the ImageView methods in the non-main thread.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }

            if (fragmentReference != null) {
                final ExampleViewerFragment fragment = fragmentReference.get();
                if (fragment != null) {
                    fragment.showLoadingView(false);
                }
            }
        }
    }

    private void showLoadingView(boolean show) {
        if (mLoadingView == null) {
            mLoadingView = mRootView.findViewById(R.id.loading_spinner);
        }

        mLoadingView.setVisibility(show ? View.VISIBLE : View.GONE);
    }


}
