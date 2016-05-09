package com.crossbowffs.quotelock.app;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import com.crossbowffs.quotelock.api.VnaasApiManager;
import com.crossbowffs.quotelock.api.VnaasQuoteQueryParams;
import com.crossbowffs.quotelock.model.VnaasQuote;
import com.crossbowffs.quotelock.preferences.PrefKeys;
import com.crossbowffs.quotelock.utils.JobUtils;
import com.crossbowffs.quotelock.utils.Xlog;

import java.io.IOException;

public class QuoteDownloaderService extends JobService {
    private static final String TAG = QuoteDownloaderService.class.getSimpleName();

    private class QuoteUpdaterTask extends AsyncTask<Void, Void, VnaasQuote> {
        private final JobParameters mJobParameters;

        public QuoteUpdaterTask(JobParameters parameters) {
            mJobParameters = parameters;
        }

        @Override
        protected VnaasQuote doInBackground(Void... params) {
            Xlog.i(TAG, "Attempting to download new VNaaS quote...");

            VnaasQuoteQueryParams query = new VnaasQuoteQueryParams();
            SharedPreferences preferences = getSharedPreferences(PrefKeys.PREF_COMMON, MODE_PRIVATE);

            String novelIdsStr = preferences.getString(PrefKeys.PREF_COMMON_ENABLED_NOVELS, null);
            if (novelIdsStr != null) {
                String[] novelIdsSplit = novelIdsStr.split(",");
                long[] novelIds = new long[novelIdsSplit.length];
                for (int i = 0; i < novelIds.length; ++i) {
                    novelIds[i] = Long.parseLong(novelIdsSplit[i]);
                }
                query.setNovels(novelIds);
            }

            String characterIdsStr = preferences.getString(PrefKeys.PREF_COMMON_ENABLED_CHARACTERS, null);
            if (characterIdsStr != null) {
                String[] characterIdsSplit = characterIdsStr.split(",");
                long[] characterIds = new long[characterIdsSplit.length];
                for (int i = 0; i < characterIds.length; ++i) {
                    characterIds[i] = Long.parseLong(characterIdsSplit[i]);
                }
                query.setCharacters(characterIds);
            }

            String contains = preferences.getString(PrefKeys.PREF_COMMON_QUOTE_CONTAINS, null);
            if (contains != null) {
                query.setContains(contains);
            }

            try {
                return mApiManager.getRandomQuote(query);
            } catch (IOException e) {
                Xlog.e(TAG, "Quote download failed", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(VnaasQuote vnaasQuote) {
            jobFinished(mJobParameters, vnaasQuote == null);
            mUpdaterTask = null;
            if (vnaasQuote != null) {
                Xlog.i(TAG, "Downloaded new VNaaS quote");
                getSharedPreferences(PrefKeys.PREF_QUOTES, MODE_PRIVATE)
                    .edit()
                    .putString(PrefKeys.PREF_QUOTES_TEXT, vnaasQuote.getText())
                    .putString(PrefKeys.PREF_QUOTES_CHARACTER, vnaasQuote.getCharacter().getName())
                    .putString(PrefKeys.PREF_QUOTES_NOVEL, vnaasQuote.getNovel().getName())
                    .apply();
                JobUtils.createQuoteDownloadJob(QuoteDownloaderService.this);
            }
        }

        @Override
        protected void onCancelled(VnaasQuote vnaasQuote) {
            mUpdaterTask = null;
        }
    }

    private VnaasApiManager mApiManager;
    private QuoteUpdaterTask mUpdaterTask;

    @Override
    public boolean onStartJob(JobParameters params) {
        if (params.getJobId() != JobUtils.JOB_ID) {
            // For some reason old jobs aren't cleared when updating the app,
            // so this is a workaround to make sure we don't kill our server :-(
            // Sadly, there's no way to get rid of the old job, so we just ignore it
            return false;
        }
        createApiManager();
        mUpdaterTask = new QuoteUpdaterTask(params);
        mUpdaterTask.execute();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mUpdaterTask != null && mUpdaterTask.getStatus() != AsyncTask.Status.FINISHED) {
            mUpdaterTask.cancel(true);
            return true;
        }
        return false;
    }

    private void createApiManager() {
        SharedPreferences preferences = getSharedPreferences(PrefKeys.PREF_COMMON, MODE_PRIVATE);
        String apiUrl = preferences.getString(PrefKeys.PREF_COMMON_API_URL, PrefKeys.PREF_COMMON_API_URL_DEFAULT);
        mApiManager = new VnaasApiManager(apiUrl);
        Xlog.i(TAG, "Created API manager with base URL: %s", apiUrl);
    }
}