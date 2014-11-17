package de.ph1b.audiobook.dialog;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.melnykov.fab.FloatingActionButton;

import de.ph1b.audiobook.R;

public class FolderChangerDialog extends DialogFragment {

    private static final String TAG = "FolderChangerDialog";


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        LayoutInflater inflater = getActivity().getLayoutInflater();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        //suppress because dialog!
        @SuppressLint("InflateParams") View v = inflater.inflate(R.layout.dialog_folder_changer, null);
        builder.setView(v);

        builder.setTitle(R.string.folder_choose_title);
        builder.setMessage(R.string.folder_choose_content);


        FloatingActionButton button = (FloatingActionButton) v.findViewById(R.id.fab);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FolderAdderDialog dialog = new FolderAdderDialog();
                dialog.show(getFragmentManager(), TAG);
            }
        });


        builder.setPositiveButton(R.string.dialog_confirm, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                //todo
            }
        });
        builder.setNegativeButton(R.string.dialog_cancel, null);


        return builder.create();
    }


}