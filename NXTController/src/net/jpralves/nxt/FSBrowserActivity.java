package net.jpralves.nxt;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

/**
 * FSBrowser Activity
 * <P>
 * Allows user to navigate in the filesystem of the android and open programs
 * with registered applications
 * 
 * @author Joao Alves
 * @version 1.0
 */
public class FSBrowserActivity extends ListActivity {

	private List<Map<String, ?>> folderEntries = new ArrayList<Map<String, ?>>();
	private File currentFolder = new File("/");

	private TextView pathTV;
	private TextView summaryTV;

	int numFiles = 0;
	int numFolders = 0;
	long filesize = 0;

	FileFillTask fileFillTask;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_fsbrowser);

		pathTV = (TextView) findViewById(R.id.path);
		summaryTV = (TextView) findViewById(R.id.summary);
		browseToRoot();
	}

	@SuppressLint("NewApi")
	@Override
	protected void onStart() {
		super.onStart();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			ActionBar actionBar = getActionBar();
			if (actionBar != null)
				actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home: {
			finish();
		}
		}
		return false;
	}

	/**
	 * Browses to the root-directory of the file-system.
	 */
	private void browseToRoot() {
		browseTo(new File("/"));
	}

	/**
	 * Browses up one level according to the currentDirectory
	 */
	private void upOneLevel() {
		if (this.currentFolder.getParent() != null)
			this.browseTo(this.currentFolder.getParentFile());
	}

	private void browseTo(final File selectedFileEntry) {
		if (selectedFileEntry.isDirectory()) {
			if (selectedFileEntry.canRead()) {
				fill(selectedFileEntry);
			} else {
				String title = String.format(getString(R.string.fsbrowser_cantberead),
						selectedFileEntry.getName());
				new AlertDialog.Builder(this)
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setTitle(title)
						.setPositiveButton(getString(R.string.ok),
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int id) {
									}
								}).show();
			}
		} else {
			OnClickListener okButtonListener = new OnClickListener() {
				// @Override
				public void onClick(DialogInterface arg0, int arg1) {
					MimeTypeMap map = MimeTypeMap.getSingleton();
					Uri uri = Uri.parse("file://" + selectedFileEntry.getAbsolutePath());
					String ext = MimeTypeMap.getFileExtensionFromUrl(selectedFileEntry.getName());
					String type = map.getMimeTypeFromExtension(ext);
					Intent myIntent = new Intent(android.content.Intent.ACTION_VIEW, uri);
					myIntent.setDataAndType(uri, type);
					startActivity(myIntent);
				}
			};
			OnClickListener cancelButtonListener = new OnClickListener() {
				// @Override
				public void onClick(DialogInterface arg0, int arg1) {
					// Do nothing
				}
			};
			new AlertDialog.Builder(this)
					.setTitle(getString(R.string.fsbrowser_action))
					.setMessage(
							getString(R.string.fsbrowser_openfile) + "\n"
									+ selectedFileEntry.getName())
					.setPositiveButton(getString(R.string.ok), okButtonListener)
					.setNegativeButton(getString(R.string.cancel), cancelButtonListener).show();
		}
	}

	/**
	 * Adds a file to the list and verifies if it is a folder
	 * 
	 * @param filename
	 *            the filename
	 * @param size
	 *            the size of the file
	 * @param isFolder
	 *            true if it is a folder
	 */
	private void addFileToList(List<Map<String, ?>> folderList, String filename, Long size,
			boolean isFolder) {

		Map<String, String> row = new HashMap<String, String>();
		row.put("fname", filename);
		if (isFolder) {
			row.put("size", "DIR");
		} else {
			row.put("size", Utils.formatMultibyte(size, true));
		}
		// folderEntries.add(row);
		folderList.add(row);
	}

	private class FileFillTask extends AsyncTask<Void, Integer, Void> {
		private ProgressDialog dialog;
		private Context context;
		private String msg = "";
		private File selectedFileEntry;
		private int currentPathStringLenght;
		private int contador = 0;
		private List<Map<String, ?>> shallowCopy;

		public FileFillTask(Context c, File selectedFileEntry) {
			context = c;
			this.selectedFileEntry = selectedFileEntry;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			Thread.currentThread().setName("FileFillTask - " + currentFolder.getAbsolutePath());
			dialog = new ProgressDialog(context);
			dialog.setMessage(getString(R.string.msg_loading));
			dialog.setIndeterminate(false);
			dialog.setCancelable(true);
			dialog.setMax(selectedFileEntry.listFiles().length);
			dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

			dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					fileFillTask.cancel(true);
				}
			});
			dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel),
					new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog, int which) {
							fileFillTask.cancel(true);
						}
					});
			dialog.show();

			numFiles = 0;
			numFolders = 0;
			filesize = 0;

			shallowCopy = new ArrayList<Map<String, ?>>();
		}

		@Override
		protected Void doInBackground(Void... arg0) {

			// Add the "." and the ".." == 'Up one level'
			addFileToList(shallowCopy, getString(R.string.this_dir), 0L, true);

			if (selectedFileEntry.getParent() != null)
				addFileToList(shallowCopy, getString(R.string.parent_dir), 0L, true);

			currentPathStringLenght = selectedFileEntry.getAbsolutePath().length() == 1 ? 0
					: selectedFileEntry.getAbsolutePath().length();
			for (File file : selectedFileEntry.listFiles()) {
				addFileToList(
						shallowCopy,
						file.getAbsolutePath().substring(currentPathStringLenght + 1)
								+ (file.isDirectory() ? File.separator : ""), file.length(),
						file.isDirectory());
				if (file.isDirectory())
					numFolders++;
				if (file.isFile()) {
					numFiles++;
					filesize += file.length();
				}
				msg = file.getAbsolutePath().substring(currentPathStringLenght + 1);
				publishProgress(contador++);
				if (isCancelled())
					break;
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... value) {
			super.onProgressUpdate(value);
			dialog.setMessage(msg);
			dialog.setProgress(value[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			if (dialog.isShowing()) {
				dialog.dismiss();
			}
			if (!isCancelled()) {

				summaryTV.setText(String.format(getString(R.string.fsbrowser_status_line),
						numFolders, numFiles, Utils.formatMultibyte(filesize, true)));

				// "Folders: " + numFolders + ", Files: " + numFiles +
				// ", Size: "
				// + Utils.formatMultibyte(filesize, true));
				if (!folderEntries.isEmpty()) {
					folderEntries.clear();
				}
				folderEntries = new ArrayList<Map<String, ?>>(shallowCopy);
				// for (Map<String, ?> sh : shallowCopy) {
				// addFileToList(folderEntries, (String) sh.get("fname"),
				// (String) sh.get("size"));
				// }

				SimpleAdapter folderList = new SimpleAdapter(FSBrowserActivity.this, folderEntries,
						R.layout.fsb_row, new String[] { "fname", "size" }, new int[] {
								R.id.fsb_name, R.id.fsb_size });
				setListAdapter(folderList);
				pathTV.setText(selectedFileEntry.getAbsolutePath());
				currentFolder = selectedFileEntry;
			}
		}
	}

	/**
	 * Fills a list with file and folder entries
	 * 
	 * @param selectedFileEntry
	 */
	private void fill(File selectedFileEntry) {

		fileFillTask = new FileFillTask(this, selectedFileEntry);
		fileFillTask.execute();
	}

	protected void onListItemClick(ListView l, View v, int position, long id) {

		Map<String, ?> selectedFile = this.folderEntries.get(position); // selectionRowID
		if (selectedFile.get("fname").equals(getString(R.string.this_dir))) {
			// Refresh
			this.browseTo(this.currentFolder);
		} else if (selectedFile.get("fname").equals(getString(R.string.parent_dir))) {
			this.upOneLevel();
		} else {
			File clickedFile = null;
			clickedFile = new File(this.currentFolder.getAbsolutePath() + File.separator
					+ selectedFile.get("fname"));
			if (clickedFile != null)
				this.browseTo(clickedFile);
		}
	}
}
