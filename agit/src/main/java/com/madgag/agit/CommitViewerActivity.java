package com.madgag.agit;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.animation.AnimationUtils.loadAnimation;
import static com.google.common.collect.Maps.newEnumMap;
import static com.madgag.agit.Relation.CHILD;
import static com.madgag.agit.Relation.PARENT;
import static org.eclipse.jgit.lib.Constants.HEAD;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.revplot.PlotLane;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.revwalk.RevCommit;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.widget.TextView;

import com.google.common.base.Function;
import com.google.inject.Inject;
import com.google.inject.internal.Nullable;
import com.google.inject.name.Named;
import com.madgag.agit.CommitNavigationView.CommitSelectedListener;

public class CommitViewerActivity extends RepositoryActivity {
	private static final String TAG = "CVA";
	
	private final static int TAG_ID=Menu.FIRST;
	
    public static Function<RevCommit, Intent> commitViewerIntentCreatorFor(final File gitdir, final Ref branch) {
		return new Function<RevCommit, Intent>() {
			public Intent apply(RevCommit commit) {
				return commitViewerIntentBuilderFor(gitdir).branch(branch).commit(commit).toIntent();
			}
		};
	}
    
    public static Intent revCommitViewIntentFor(File gitdir, String commitId) {
		return commitViewerIntentBuilderFor(gitdir).commit(commitId).toIntent();
	}
    
	private static GitIntentBuilder commitViewerIntentBuilderFor(File gitdir) {
		return new GitIntentBuilder("git.view.COMMIT").gitdir(gitdir);
	}

	@Inject @Named("branch") @Nullable Ref branch;
	
	private PlotCommit<PlotLane> commit;
	
	private CommitView currentCommitView, nextCommitView;
	
	private Map<Relation,RelationAnimations> relationAnimations = newEnumMap(Relation.class);
	
	private class RelationAnimations {
		private final Animation animateOldViewOut,animateNewViewIn;
		
		public RelationAnimations(int animateOldViewOutId, int animateNewViewInId) {
			animateOldViewOut=loadAnimation(CommitViewerActivity.this,animateOldViewOutId);
			animateNewViewIn=loadAnimation(CommitViewerActivity.this,animateNewViewInId);
		}
		
		void animateViews() {
			currentCommitView.startAnimation(animateOldViewOut);
			nextCommitView.startAnimation(animateNewViewIn);
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.commit_navigation_animation_layout);

		relationAnimations.put(PARENT, new RelationAnimations(R.anim.push_child_out, R.anim.pull_parent_in));
		relationAnimations.put(CHILD, new RelationAnimations(R.anim.push_parent_out, R.anim.pull_child_in));
		
		currentCommitView = (CommitView) findViewById(R.id.commit_nav_current_commit);
		nextCommitView = (CommitView) findViewById(R.id.commit_nav_next_commit);
		
		CommitSelectedListener commitSelectedListener = new CommitSelectedListener() {
			public void onCommitSelected(Relation relation, PlotCommit<PlotLane> commit) {
				setCommit(commit, relation);
			}
		};
		try {

			
			ObjectId revisionId = GitIntents.commitIdFrom(getIntent()); // intent.getStringExtra("commit");
			Log.i("RCCV", revisionId.getName());
			PlotWalk revWalk = generatePlotWalk();
			
			commit = (PlotCommit<PlotLane>) revWalk.parseCommit(revisionId);
			
			setup(currentCommitView, commitSelectedListener, revWalk);
			setup(nextCommitView, commitSelectedListener, revWalk);
			
			currentCommitView.setCommit(commit);
		    setCurrentCommitViewVisible();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void setup(CommitView commitView,
			CommitSelectedListener commitSelectedListener,
			PlotWalk revWalk) {
		commitView.setRepositoryContext(repo(), revWalk);
		commitView.setCommitSelectedListener(commitSelectedListener);
	}

	

	private void setCurrentCommitViewVisible() {
		currentCommitView.setVisibility(VISIBLE);
		nextCommitView.setVisibility(GONE);
	}
	
	public void setCommit(PlotCommit<PlotLane> newCommit, Relation relation) {
		this.commit = newCommit;
		try {
			nextCommitView.setCommit(newCommit);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		relationAnimations.get(relation).animateViews();
		CommitView oldCurrent = currentCommitView;
		currentCommitView = nextCommitView;
		nextCommitView = oldCurrent;
		setCurrentCommitViewVisible();
	}

	private PlotWalk generatePlotWalk() throws AmbiguousObjectException,
			IOException, MissingObjectException, IncorrectObjectTypeException {
		PlotWalk revWalk = new PlotWalk(repo());
		ObjectId rootId = (branch==null)?repo().resolve(HEAD):branch.getObjectId();
		Log.d(TAG,"Using root "+rootId+" branch="+branch);
		RevCommit root = revWalk.parseCommit(rootId);
		revWalk.markStart(root);
		PlotCommitList<PlotLane> plotCommitList = new PlotCommitList<PlotLane>();
		plotCommitList.source(revWalk);
		plotCommitList.fillTo(Integer.MAX_VALUE);
		return revWalk;
	}
	
	final int CREATE_TAG_DIALOG=0;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
        menu.add(0, TAG_ID, 0, R.string.tag_commit_menu_option).setShortcut('0', 't');
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case TAG_ID:
        	showDialog(CREATE_TAG_DIALOG);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case CREATE_TAG_DIALOG:
            LayoutInflater factory = LayoutInflater.from(this);
            final View textEntryView = factory.inflate(R.layout.create_tag_dialog, null);
            return new AlertDialog.Builder(this)
//                .setIcon(R.drawable.alert_dialog_icon)
//                .setTitle(R.string.alert_dialog_text_entry)
                .setView(textEntryView)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    	 String tagName=((TextView) textEntryView.findViewById(R.id.tag_name_edit)).getText().toString();
                    	 try {
							new Git(repo()).tag().setName(tagName).setObjectId(commit).call();
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
                    }
                })
                .create();
        }
        return null;
    }

	@Override
	String TAG() { return TAG; }

}