/*
 * Copyright (C) 2015 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.resolve.checkpoint;

import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import org.opendatakit.IntentConsts;
import org.opendatakit.common.android.database.OdkConnectionFactorySingleton;
import org.opendatakit.common.android.database.OdkConnectionInterface;
import org.opendatakit.common.android.utilities.ODKDatabaseImplUtils;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.core.R;
import org.opendatakit.database.service.OdkDbHandle;
import org.opendatakit.resolve.views.components.ResolveRowEntry;

import java.util.ArrayList;
import java.util.UUID;

/**
 * @author mitchellsundt@gmail.com
 */
public class CheckpointResolutionListFragment extends ListFragment implements LoaderManager
    .LoaderCallbacks<ArrayList<ResolveRowEntry>> {

  private static final String TAG = "CheckpointResolutionListFragment";
  private static final int RESOLVE_ROW_LOADER = 0x02;

  public static final String NAME = "CheckpointResolutionListFragment";
  public static final int ID = R.layout.checkpoint_resolver_chooser_list;

  private String mAppName;
  private String mTableId;
  private ArrayAdapter<ResolveRowEntry> mAdapter;

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    Intent incomingIntent = getActivity().getIntent();
    mAppName = incomingIntent.getStringExtra(IntentConsts.INTENT_KEY_APP_NAME);
    if ( mAppName == null || mAppName.length() == 0 ) {
      getActivity().setResult(Activity.RESULT_CANCELED);
      getActivity().finish();
      return;
    }

    mTableId = incomingIntent.getStringExtra(IntentConsts.INTENT_KEY_TABLE_ID);
    if ( mTableId == null || mTableId.length() == 0 ) {
      getActivity().setResult(Activity.RESULT_CANCELED);
      getActivity().finish();
      return;
    }

    // render total instance view
    mAdapter = new ArrayAdapter<ResolveRowEntry>(getActivity(), android.R.layout
        .simple_list_item_1);
    setListAdapter(mAdapter);

    getLoaderManager().initLoader(RESOLVE_ROW_LOADER, null, this);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(ID, container, false);
    Button buttonTakeAllOldest = (Button) view.findViewById(R.id.take_all_oldest);
    Button buttonTakeAllNewest = (Button) view.findViewById(R.id.take_all_newest);
    buttonTakeAllNewest.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        takeAllNewest();
      }
    });
    buttonTakeAllOldest.setOnClickListener(new View.OnClickListener() {
      @Override public void onClick(View v) {
        takeAllOldest();
      }
    });
    return view;
  }

  private void takeAllNewest() {
    if ( mAdapter == null ) return;

    OdkConnectionInterface db = null;

    OdkDbHandle dbHandleName = new OdkDbHandle(UUID.randomUUID().toString());

    StringBuilder exceptions = null;

    try {
      // +1 referenceCount if db is returned (non-null)
      db = OdkConnectionFactorySingleton.getOdkConnectionFactoryInterface()
          .getConnection(mAppName, dbHandleName);

      for ( int i = 0 ; i < mAdapter.getCount() ; ++i ) {
        ResolveRowEntry entry = mAdapter.getItem(i);
        try {
          ODKDatabaseImplUtils.get()
              .saveAsCompleteMostRecentCheckpointDataInDBTableWithId(db, mTableId, entry.rowId);
        } catch (Exception e) {
          String msg = e.getLocalizedMessage();
          if (msg == null)
            msg = e.getMessage();
          if (msg == null)
            msg = e.toString();
          msg = "Exception: " + msg;
          WebLogger.getLogger(mAppName).e("takeAllNewest",
              mAppName + " " + dbHandleName.getDatabaseHandle() + " " + msg);
          WebLogger.getLogger(mAppName).printStackTrace(e);

          if (exceptions == null) {
            exceptions = new StringBuilder();
          } else {
            exceptions.append("\n");
          }
          exceptions.append(msg);

          // and to be sure, try to release this database connection and create a new one...
          OdkConnectionInterface dbOld = db;
          db = null;

          dbHandleName = new OdkDbHandle(UUID.randomUUID().toString());

          if ( dbOld != null ) {
            dbOld.releaseReference();
          }

          // +1 referenceCount if db is returned (non-null)
          db = OdkConnectionFactorySingleton.getOdkConnectionFactoryInterface()
              .getConnection(mAppName, dbHandleName);
        }
      }

    } finally {
      if (db != null) {
        // release the reference...
        // this does not necessarily close the db handle
        // or terminate any pending transaction
        db.releaseReference();
      }

      if ( exceptions != null ) {
        Toast.makeText(getActivity(), exceptions.toString(), Toast.LENGTH_LONG).show();
      }
    }
    getLoaderManager().restartLoader(RESOLVE_ROW_LOADER, null, this);
  }

  private void takeAllOldest() {
    if ( mAdapter == null ) return;

    OdkConnectionInterface db = null;

    OdkDbHandle dbHandleName = new OdkDbHandle(UUID.randomUUID().toString());

    StringBuilder exceptions = null;

    try {
      // +1 referenceCount if db is returned (non-null)
      db = OdkConnectionFactorySingleton.getOdkConnectionFactoryInterface()
          .getConnection(mAppName, dbHandleName);

      for ( int i = 0 ; i < mAdapter.getCount() ; ++i ) {
        ResolveRowEntry entry = mAdapter.getItem(i);
        try {
          ODKDatabaseImplUtils.get().deleteCheckpointRowsWithId(db, mAppName, mTableId, entry.rowId);
        } catch (Exception e) {
          String msg = e.getLocalizedMessage();
          if (msg == null)
            msg = e.getMessage();
          if (msg == null)
            msg = e.toString();
          msg = "Exception: " + msg;
          WebLogger.getLogger(mAppName).e("takeAllOldest",
              mAppName + " " + dbHandleName.getDatabaseHandle() + " " + msg);
          WebLogger.getLogger(mAppName).printStackTrace(e);

          if (exceptions == null) {
            exceptions = new StringBuilder();
          } else {
            exceptions.append("\n");
          }
          exceptions.append(msg);

          // and to be sure, try to release this database connection and create a new one...
          OdkConnectionInterface dbOld = db;
          db = null;

          dbHandleName = new OdkDbHandle(UUID.randomUUID().toString());

          if ( dbOld != null ) {
            dbOld.releaseReference();
          }

          // +1 referenceCount if db is returned (non-null)
          db = OdkConnectionFactorySingleton.getOdkConnectionFactoryInterface()
              .getConnection(mAppName, dbHandleName);
        }
      }

    } finally {
      if (db != null) {
        // release the reference...
        // this does not necessarily close the db handle
        // or terminate any pending transaction
        db.releaseReference();
      }

      if ( exceptions != null ) {
        Toast.makeText(getActivity(), exceptions.toString(), Toast.LENGTH_LONG).show();
      }
    }
    getLoaderManager().restartLoader(RESOLVE_ROW_LOADER, null, this);
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    super.onListItemClick(l, v, position, id);
    ResolveRowEntry e = mAdapter.getItem(position);
    WebLogger.getLogger(mAppName).e(TAG,
        "[onListItemClick] clicked position: " + position + " rowId: " + e.rowId);
    launchRowResolution(e);
  }

  private void launchRowResolution(ResolveRowEntry e) {
    Intent i = new Intent(getActivity(), CheckpointResolutionActivity.class);
    i.putExtra(IntentConsts.INTENT_KEY_APP_NAME, mAppName);
    i.putExtra(IntentConsts.INTENT_KEY_TABLE_ID, mTableId);
    i.putExtra(IntentConsts.INTENT_KEY_INSTANCE_ID, e.rowId);
    this.startActivityForResult(i, CheckpointResolutionActivity.RESOLVE_ROW);
  }

  @Override
  public Loader<ArrayList<ResolveRowEntry>> onCreateLoader(int id, Bundle args) {
    // Now create and return a OdkResolveCheckpointRowLoader that will take care of
    // creating an ArrayList<ResolveRowEntry> for the data being displayed.
    return new OdkResolveCheckpointRowLoader(getActivity(), mAppName, mTableId);
  }

  @Override
  public void onLoadFinished(Loader<ArrayList<ResolveRowEntry>> loader,
      ArrayList<ResolveRowEntry> resolveRowEntryArrayList) {
    // Swap the new cursor in. (The framework will take care of closing the
    // old cursor once we return.)
    mAdapter.clear();
    if ( resolveRowEntryArrayList.size() == 1 ) {
      launchRowResolution(resolveRowEntryArrayList.get(0));
      return;
    } else if ( resolveRowEntryArrayList.isEmpty() ){
      getActivity().setResult(Activity.RESULT_OK);
      getActivity().finish();
      return;
    }
    mAdapter.addAll(resolveRowEntryArrayList);

    if ( getView() == null ) {
      throw new IllegalStateException("Unexpectedly found no view!");
    }

    mAdapter.notifyDataSetChanged();
  }

  @Override
  public void onLoaderReset(Loader<ArrayList<ResolveRowEntry>> loader) {
    // This is called when the last ArrayList<ResolveRowEntry> provided to onLoadFinished()
    // above is about to be released. We need to make sure we are no
    // longer using it.
    mAdapter.clear();
  }

}
