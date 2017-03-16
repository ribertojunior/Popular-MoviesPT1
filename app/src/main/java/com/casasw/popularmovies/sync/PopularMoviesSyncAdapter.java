package com.casasw.popularmovies.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.casasw.popularmovies.BuildConfig;
import com.casasw.popularmovies.R;
import com.casasw.popularmovies.Utilities;
import com.casasw.popularmovies.data.MovieContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Vector;

import static com.casasw.popularmovies.Utilities.uriMaker;

public class PopularMoviesSyncAdapter extends AbstractThreadedSyncAdapter {
    public final String LOG_TAG = PopularMoviesSyncAdapter.class.getSimpleName();
    // Interval at which to sync with the weather, in milliseconds.
    // 60 seconds (1 minute) * 180 = 3 hours
    public static final int SYNC_INTERVAL = 60*180;
    public static final int SYNC_FLEXTIME = SYNC_INTERVAL/3;


    public PopularMoviesSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        String movieList = Utilities.getMoviesList(getContext());


        try {
            String jsonStr;
            //fetching movie data
            String path[] = {"3", "movie", movieList};
            URL url = new URL(uriMaker(
                    "api.themoviedb.org", path, "api_key",
                    BuildConfig.MOVIE_API_KEY).toString());

            final String[] PARAMS = {"results","poster_path","overview",
                    "original_title","backdrop_path","vote_average",
                    "release_date", "id"};
            jsonStr = fetchJSonData(url);
            String[] idList = insertMovieDataFromJson(jsonStr ,movieList, PARAMS);





        }catch (Exception e) {
            Log.e(LOG_TAG, e.toString());
        }


    }

    private String[] insertMovieDataFromJson(String moviesJsonStr, String movieList, String[] params)  throws JSONException{
        JSONObject moviesJson = new JSONObject(moviesJsonStr);
        JSONArray resultJsonArray = moviesJson.getJSONArray(params[0]);
        Vector<ContentValues> contentValuesVector = new Vector<ContentValues>(resultJsonArray.length());

        String[] idList = new String[resultJsonArray.length()];
        JSONObject movieListJSON;
        for (int i = 0; i< resultJsonArray.length(); i++) {
            long id;
            String poster_path;
            String overview;
            String original_title;
            String backdrop_path;
            String vote_average;
            String release_date;

            movieListJSON = resultJsonArray.getJSONObject(i);
            id = movieListJSON.getLong(params[7]);
            poster_path = movieListJSON.getString(params[1]);
            overview = movieListJSON.getString(params[2]);
            original_title = movieListJSON.getString(params[3]);
            backdrop_path = movieListJSON.getString(params[4]);
            vote_average = movieListJSON.getString(params[5]);
            release_date = movieListJSON.getString(params[6]);

            idList[i] = movieListJSON.getString(params[7]);

            ContentValues movieValues = new ContentValues();

            movieValues.put(MovieContract.MovieEntry.COLUMN_MOVIE_ID, id);
            movieValues.put(MovieContract.MovieEntry.COLUMN_POSTER_PATH, poster_path);
            movieValues.put(MovieContract.MovieEntry.COLUMN_OVERVIEW, overview);
            movieValues.put(MovieContract.MovieEntry.COLUMN_ORIGINAL_TITLE, original_title);
            movieValues.put(MovieContract.MovieEntry.COLUMN_BACKDROP_PATH, backdrop_path);
            movieValues.put(MovieContract.MovieEntry.COLUMN_VOTE_AVERAGE, vote_average);
            movieValues.put(MovieContract.MovieEntry.COLUMN_RELEASE_DATE, release_date);
            movieValues.put(MovieContract.MovieEntry.COLUMN_MOVIE_LIST, movieList);
            movieValues.put(MovieContract.MovieEntry.COLUMN_POSITION, i);

            contentValuesVector.add(movieValues);

        }
        int in;
        if (contentValuesVector.size() > 0) {
            ContentValues[] cvArray = new ContentValues[contentValuesVector.size()];
            contentValuesVector.toArray(cvArray);
            in = getContext().getContentResolver().bulkInsert(MovieContract.MovieEntry.CONTENT_URI, cvArray);
        }
        return idList;
    }

    @Nullable
    private String fetchJSonData(URL url) {
        HttpURLConnection urlConnection = null;
        BufferedReader reader = null;

        String jSonStr;

        try {
            ////Log.v(LOG_TAG, url.toString());
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();
            InputStream inputStream = urlConnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null) {
                jSonStr = null;
            }
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                // But it does make debugging a *lot* easier if you print out the completed
                // buffer for debugging.
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0) {
                // Stream was empty.  No point in parsing.
                jSonStr = null;
            }
            jSonStr = buffer.toString();
            return jSonStr;

        }catch (Exception e){
            Log.e(LOG_TAG, e.toString());

        }finally {
            if (urlConnection != null){
                urlConnection.disconnect();
            }
            if (reader != null){
                try {
                    reader.close();
                } catch (final IOException e) {
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }

        }
        return null;

    }

     /**
     * Helper method to have the sync adapter sync immediately
     * @param context The context used to access the account service
    **/

    public static void syncImmediately(Context context) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        ContentResolver.requestSync(getSyncAccount(context),
                context.getString(R.string.content_authority), bundle);
    }

     /**
     * Helper method to get the fake account to be used with SyncAdapter, or make a new one
     * if the fake account doesn't exist yet.  If we make a new account, we call the
     * onAccountCreated method so we can initialize things.
     *
     * @param context The context used to access the account service
     * @return a fake account.
      * */


    public static Account getSyncAccount(Context context) {
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account
        Account newAccount = new Account(
                context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist
        if ( null == accountManager.getPassword(newAccount) ) {

         /** Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
          * */


            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
             /** If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call ContentResolver.setIsSyncable(account, AUTHORITY, 1)
             * here.
              * */


            onAccountCreated(newAccount, context);

        }
        return newAccount;
    }

    /**
     * Helper method to schedule the sync adapter periodic execution
     * */


    public static void configurePeriodicSync(Context context, int syncInterval, int flexTime) {
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // we can enable inexact timers in our periodic sync
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(syncInterval, flexTime).
                    setSyncAdapter(account, authority).
                    setExtras(new Bundle()).build();
            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account,
                    authority, new Bundle(), syncInterval);
        }
    }


    private static void onAccountCreated(Account newAccount, Context context) {
         // Since we've created an account


        PopularMoviesSyncAdapter.configurePeriodicSync(context, SYNC_INTERVAL, SYNC_FLEXTIME);

        // Without calling setSyncAutomatically, our periodic sync will not be enabled.


        ContentResolver.setSyncAutomatically(newAccount, context.getString(R.string.content_authority), true);

         // Finally, let's do a sync to get things started


        syncImmediately(context);
    }

    public static void initializeSyncAdapter(Context context) {
        getSyncAccount(context);
    }
}



