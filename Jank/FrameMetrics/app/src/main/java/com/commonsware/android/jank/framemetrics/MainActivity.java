/***
  Copyright (c) 2008-2016 CommonsWare, LLC
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain	a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
  by applicable law or agreed to in writing, software distributed under the
  License is distributed on an "AS IS" BASIS,	WITHOUT	WARRANTIES OR CONDITIONS
  OF ANY KIND, either express or implied. See the License for the specific
  language governing permissions and limitations under the License.
  
  From _The Busy Coder's Guide to Android Development_
    https://commonsware.com/Android
*/

package com.commonsware.android.jank.framemetrics;

import android.Manifest;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.FrameMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.Window;

public class MainActivity extends RecyclerViewActivity implements
    LoaderManager.LoaderCallbacks<Cursor>,
  Window.OnFrameMetricsAvailableListener {
  private static final String STATE_IN_PERMISSION="inPermission";
  private static final int REQUEST_PERMS=137;
  private boolean isInPermission=false;
  private MenuItem record, stop;
  private HandlerThread handlerThread=
    new HandlerThread("FrameMetrics",
      Process.THREAD_PRIORITY_BACKGROUND);
  private AggregateFrameMetrics afm;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);

    handlerThread.start();
    setLayoutManager(new LinearLayoutManager(this));
    setAdapter(new VideoAdapter());

    if (icicle!=null) {
      isInPermission=
        icicle.getBoolean(STATE_IN_PERMISSION, false);
    }

    if (hasFilesPermission()) {
      loadVideos();
    }
    else if (!isInPermission) {
      isInPermission=true;

      ActivityCompat.requestPermissions(this,
        new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
        REQUEST_PERMS);
    }
  }

  @Override
  protected void onDestroy() {
    handlerThread.quitSafely();

    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putBoolean(STATE_IN_PERMISSION, isInPermission);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String[] permissions,
                                         int[] grantResults) {
    isInPermission=false;

    if (requestCode==REQUEST_PERMS) {
      if (hasFilesPermission()) {
        loadVideos();
      }
      else {
        finish(); // denied permission, so we're done
      }
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.actions, menu);
    record=menu.findItem(R.id.record);
    stop=menu.findItem(R.id.stop);

    return(super.onCreateOptionsMenu(menu));
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId()==R.id.record) {
      record.setVisible(false);
      stop.setVisible(true);
      afm=new AggregateFrameMetrics();
      getWindow()
        .addOnFrameMetricsAvailableListener(this,
          new Handler(handlerThread.getLooper()));

      return(true);
    }
    else if (item.getItemId()==R.id.stop) {
      record.setVisible(true);
      stop.setVisible(false);
      getWindow().removeOnFrameMetricsAvailableListener(this);
      afm.log(getClass().getSimpleName());
      afm=null;

      return(true);
    }

    return(super.onOptionsItemSelected(item));
  }

  @Override
  public void onFrameMetricsAvailable(Window window,
                                      FrameMetrics frameMetrics,
                                      int droppedEvents) {
    afm.add(frameMetrics, droppedEvents);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return(new CursorLoader(this,
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            null, null, null,
                            MediaStore.Video.Media.TITLE));
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
    ((VideoAdapter)getAdapter()).setVideos(c);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    ((VideoAdapter)getAdapter()).setVideos(null);
  }

  private boolean hasFilesPermission() {
    return(ContextCompat.checkSelfPermission(this,
      Manifest.permission.READ_EXTERNAL_STORAGE)==
      PackageManager.PERMISSION_GRANTED);
  }

  private void loadVideos() {
    getLoaderManager().initLoader(0, null, this);
  }

  class VideoAdapter extends RecyclerView.Adapter<RowController> {
    Cursor videos=null;

    @Override
    public RowController onCreateViewHolder(ViewGroup parent, int viewType) {
      return(new RowController(getLayoutInflater()
                                 .inflate(R.layout.row, parent, false)));
    }

    void setVideos(Cursor videos) {
      this.videos=videos;
      notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(RowController holder, int position) {
      videos.moveToPosition(position);
      holder.bindModel(videos);
    }

    @Override
    public int getItemCount() {
      if (videos==null) {
        return(0);
      }

      return(videos.getCount());
    }
  }
}
