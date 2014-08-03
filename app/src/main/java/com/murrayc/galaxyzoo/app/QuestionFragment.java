/*
 * Copyright (C) 2014 Murray Cumming
 *
 * This file is part of android-galaxyzoo.
 *
 * android-galaxyzoo is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * android-galaxyzoo is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with android-galaxyzoo.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.murrayc.galaxyzoo.app;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.murrayc.galaxyzoo.app.provider.Classification;
import com.murrayc.galaxyzoo.app.provider.ClassificationAnswer;
import com.murrayc.galaxyzoo.app.provider.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * A fragment representing a single subject.
 * This fragment is either contained in a {@link com.murrayc.galaxyzoo.app.ListActivity}
 * in two-pane mode (on tablets) or a {@link com.murrayc.galaxyzoo.app.DetailActivity}
 * on handsets.
 */
public class QuestionFragment extends ItemFragment
        implements LoaderManager.LoaderCallbacks<Cursor>{

    public static final String ARG_QUESTION_ID = "question-id";

    private static final int URL_LOADER = 0;
    private Cursor mCursor;

    private final String[] mColumns = { Item.Columns._ID, Item.Columns.ZOONIVERSE_ID };

    // We have to hard-code the indices - we can't use getColumnIndex because the Cursor
    // (actually a SQliteDatabase cursor returned
    // from our ContentProvider) only knows about the underlying SQLite database column names,
    // not our ContentProvider's column names. That seems like a design error in the Android API.
    //TODO: Use org.apache.commons.lang.ArrayUtils.indexOf() instead?
    private static final int COLUMN_INDEX_ID = 0;
    static final int COLUMN_INDEX_ZOONIVERSE_ID = 1;


    //We hard-code this.
    //Alternatively, we could hard-code the removal of this question from the XML
    //when generating the XML file,
    //and then always ask the question at the end via Java code.
    private static final CharSequence QUESTION_ID_DISCUSS = "sloan-11";
    private static final CharSequence ANSWER_ID_DISCUSS_YES = "a-0";
    private String mQuestionId;
    private String mZooniverseId; //Only used for the talk URI so far.

    private void setZooniverseId(final String zooniverseId) {
        mZooniverseId = zooniverseId;
    }

    private String getZooniverseId() {
        return mZooniverseId;
    }


    /** This lets us store the classification's answers
     * during the classification. Alternatively,
     * we could insert the answers into the ContentProvider along the
     * way, but this lets us avoid having half-complete classifications
     * in the content provider.
     */
    static private class ClassificationInProgress {
        public void add(final String questionId, final String answerId) {
            answers.add(new QuestionAnswer(questionId, answerId));
        }

        static private class QuestionAnswer {
            private String questionId;
            private String answerId;

            public QuestionAnswer(final String questionId, final String answerId) {
                this.questionId = questionId;
                this.answerId = answerId;
            }

            public String getQuestionId() {
                return questionId;
            }

            public String getAnswerId() {
                return answerId;
            }
        }

        List<QuestionAnswer> getAnswers() {
            return answers;
        }

        private List<QuestionAnswer> answers = new ArrayList<>();
    }

    //TODO: Can this fragment be reused, meaning we'd need to reset this?
    private ClassificationInProgress mClassificationInProgress = new ClassificationInProgress();

    /**
     * A dummy implementation of the {@link com.murrayc.galaxyzoo.app.ListFragment.Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static final Callbacks sDummyCallbacks = new Callbacks() {

    };

    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private Callbacks mCallbacks = sDummyCallbacks;

    /**
     * A callback interface that all activities containing some fragments must
     * implement. This mechanism allows activities to be notified of
     * navigation selections.
     * <p/>
     * This is the recommended way for activities and fragments to communicate,
     * presumably because, unlike a direct function call, it still keeps the
     * fragment and activity implementations separate.
     * http://developer.android.com/guide/components/fragments.html#CommunicatingWithActivity
     */
    static interface Callbacks {

    }

    private View mRootView;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public QuestionFragment() {
    }

    public String getQuestionId() {
        return mQuestionId;
    }

    public void setQuestionId(final String questionId) {
        mQuestionId = questionId;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle bundle = getArguments();
        if (bundle != null) {
            setQuestionId(bundle.getString(ARG_QUESTION_ID));
        }

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_question, container, false);
        assert mRootView != null;

        setHasOptionsMenu(true);

        /*
         * Initializes the CursorLoader. The URL_LOADER value is eventually passed
         * to onCreateLoader().
         * This lets us get the Subject ID for the item, for use in the discussion page's URI.
         */
        getLoaderManager().initLoader(URL_LOADER, null, this);

        update();

        return mRootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        //final MenuItem menuItem = menu.add(Menu.NONE, R.id.option_menu_item_list, Menu.NONE, R.string.action_list);
        //menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = sDummyCallbacks;
    }

    public void update() {
        final Activity activity = getActivity();
        if (activity == null)
            return;


        if (mRootView == null) {
            Log.error("mRootView is null.");
            return;
        }

        final Singleton singleton = Singleton.getInstance(activity);
        final DecisionTree tree = singleton.getDecisionTree();

        DecisionTree.Question question = null;
        if (TextUtils.isEmpty(getQuestionId())) {
            question = tree.getFirstQuestion();
            setQuestionId(question.getId());
        } else {
            question = tree.getQuestion(getQuestionId());
        }

        //Show the title:
        final TextView textViewTitle = (TextView)mRootView.findViewById(R.id.textViewTitle);
        if (textViewTitle == null) {
            Log.error("textViewTitle is null.");
            return;
        }
        textViewTitle.setText(question.getTitle());

        //Show the text:
        final TextView textViewText = (TextView)mRootView.findViewById(R.id.textViewText);
        if (textViewText == null) {
            Log.error("textViewText is null.");
            return;
        }
        textViewText.setText(question.getText());


        //Answers:
        final LinearLayout layoutAnswers = (LinearLayout)mRootView.findViewById(R.id.layoutAnswers);
        if (layoutAnswers == null) {
            Log.error("layoutAnswers is null.");
            return;
        }

        layoutAnswers.removeAllViews();
        for(final DecisionTree.Answer answer : question.answers) {
            final Button button = new Button(activity);
            button.setText(answer.getText());
            layoutAnswers.addView(button);

            button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    // Perform action on click
                    onAnswerButtonClicked(answer.getId());
                }
            });
        }
    }

    private void onAnswerButtonClicked(final String answerId) {
        //TODO: Move this logic to the parent ClassifyFragment?
        final Activity activity = getActivity();
        if (activity == null)
            return;

        final String questionId = getQuestionId();
        if((TextUtils.equals(questionId, QUESTION_ID_DISCUSS)) &&
                (TextUtils.equals(answerId, ANSWER_ID_DISCUSS_YES))) {
            //Open a link to the discussion page.

            //Todo: Find a way to use Uri.Builder with a URI with # in it.
            //Using Uri.parse() (with Uri.Builder) removes the #.
            //Using Uri.Builder() leads to an ActivityNotFoundException.
            //final String encodedHash = Uri.encode("#"); //This just puts %23 in the URL instead of #.
            //final Uri.Builder uriBuilder = new Uri.Builder();
            //uriBuilder.path("http://talk.galaxyzoo.org/#/subjects/");
            //uriBuilder.appendPath(getZooniverseId());
            final String uriTalk = "http://talk.galaxyzoo.org/#/subjects/" + getZooniverseId();

            try {
                final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriTalk));
                startActivity(intent);
            } catch (final ActivityNotFoundException e) {
                Log.error("Could not open the discussion URI.", e);
            }
        } else {
            //Remember the answer:
            mClassificationInProgress.add(questionId, answerId);
        }

        final Singleton singleton = Singleton.getInstance(activity);
        final DecisionTree tree = singleton.getDecisionTree();

        final DecisionTree.Question question = tree.getNextQuestionForAnswer(questionId, answerId);
        if(question != null) {
            setQuestionId(question.getId());
            update();
        } else {
            //The classification is finished.
            //TODO: Save it to the ContentProvider, which will upload it.
            saveClassification(mClassificationInProgress);
            mClassificationInProgress = new ClassificationInProgress();

            //TODO: Do something else for tablet UIs that share the activity.
            activity.finish();
        }
    }

    private void saveClassification(final ClassificationInProgress classificationInProgress) {
        final Activity activity = getActivity();
        if (activity == null)
            return;

        final ContentResolver resolver = activity.getContentResolver();

        // Add the Classification:
        // We can't do this together with the other ContentProviderOperations,
        // because we need to get the generated Classification ID before
        // adding the Classification Answers.
        // ContentProviderOperation.Builder.withValueBackReferences() would only be useful
        // for one insert of a Classification Answer.
        final ContentValues values = new ContentValues();
        values.put(Classification.Columns.ITEM_ID, getItemId());
        final Uri uriClassification = resolver.insert(Classification.CLASSIFICATIONS_URI, values);
        final long classificationId = ContentUris.parseId(uriClassification);

        // Add the related Classification Answers:
        // Use a ContentProvider operation to perform operations together,
        // either completely or not at all, as a transaction.
        // This should prevent an incomplete classification from being uploaded
        // before we have finished adding it.
        final ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        for (final ClassificationInProgress.QuestionAnswer answer : classificationInProgress.getAnswers()) {
            final ContentProviderOperation.Builder builder =
                    ContentProviderOperation.newInsert(ClassificationAnswer.CLASSIFICATION_ANSWERS_URI);
            final ContentValues valuesAnswers = new ContentValues();
            valuesAnswers.put(ClassificationAnswer.Columns.CLASSIFICATION_ID, classificationId);
            valuesAnswers.put(ClassificationAnswer.Columns.QUESTION_ID, answer.getQuestionId());
            valuesAnswers.put(ClassificationAnswer.Columns.ANSWER_ID, answer.getAnswerId());
            builder.withValues(valuesAnswers);

            ops.add(builder.build());
        }

        //Mark the Item (Subject) as done:
        final Uri.Builder uriBuilder = Item.ITEMS_URI.buildUpon();
        uriBuilder.appendPath(getItemId());
        final ContentProviderOperation.Builder builder =
                ContentProviderOperation.newUpdate(uriBuilder.build());
        final ContentValues valuesDone = new ContentValues();
        valuesDone.put(Item.Columns.DONE, true);
        builder.withValues(valuesDone);
        ops.add(builder.build());

        try {
            resolver.applyBatch(ClassificationAnswer.AUTHORITY, ops);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (OperationApplicationException e) {
            e.printStackTrace();
        }

        //The ItemsContentProvider will upload the classification later.
    }

    private void updateFromCursor() {
        if (mCursor == null) {
            Log.error("mCursor is null.");
            return;
        }

        final Activity activity = getActivity();
        if (activity == null)
            return;

        if (mCursor.getCount() <= 0) { //In case the query returned no rows.
            Log.error("The ContentProvider query returned no rows.");
        }

        mCursor.moveToFirst(); //There should only be one anyway.

        //Look at each group in the layout:
        if (mRootView == null) {
            Log.error("mRootView is null.");
            return;
        }

        final String zooniverseId = mCursor.getString(COLUMN_INDEX_ZOONIVERSE_ID);
        setZooniverseId(zooniverseId);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int loaderId, Bundle bundle) {
        if (loaderId != URL_LOADER) {
            return null;
        }

        final String itemId = getItemId();
        if (TextUtils.isEmpty(itemId)) {
            return null;
        }

        final Activity activity = getActivity();

        final Uri.Builder builder = Item.CONTENT_URI.buildUpon();
        builder.appendPath(itemId);

        return new CursorLoader(
                activity,
                builder.build(),
                mColumns,
                null, // No where clause, return all records. We already specify just one via the itemId in the URI
                null, // No where clause, therefore no where column values.
                null // Use the default sort order.
        );
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mCursor = cursor;
        updateFromCursor();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        /*
         * Clears out our reference to the Cursor.
         * This prevents memory leaks.
         */
        mCursor = null;
    }
}
