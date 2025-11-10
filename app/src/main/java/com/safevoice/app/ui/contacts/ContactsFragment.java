package com.safevoice.app.ui.contacts;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.safevoice.app.R;
import com.safevoice.app.databinding.FragmentContactsBinding;
import com.safevoice.app.models.Contact;
import com.safevoice.app.utils.ContactsManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The fragment for the "Contacts" screen.
 * It displays the primary and priority contacts and allows the user to manage them.
 */
public class ContactsFragment extends Fragment implements ContactsAdapter.OnContactOptionsClickListener {

    private FragmentContactsBinding binding;
    private ContactsManager contactsManager;
    private ContactsAdapter contactsAdapter;
    private List<Contact> priorityContactList;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentContactsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        contactsManager = ContactsManager.getInstance(requireContext());
        priorityContactList = new ArrayList<>();

        // Setup RecyclerView
        binding.recyclerViewContacts.setLayoutManager(new LinearLayoutManager(getContext()));
        contactsAdapter = new ContactsAdapter(priorityContactList, this);
        binding.recyclerViewContacts.setAdapter(contactsAdapter);

        // Setup button click listeners
        binding.buttonSetPrimaryContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddEditContactDialog(null, true); // true for primary contact
            }
        });

        binding.buttonAddPriorityContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddEditContactDialog(null, false); // false for priority contact
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload contacts every time the fragment is shown to ensure data is fresh.
        loadContacts();
    }

    /**
     * Loads all contacts from the ContactsManager and updates the UI.
     */
    private void loadContacts() {
        // Load and display the primary contact
        Contact primaryContact = contactsManager.getPrimaryContact();
        if (primaryContact != null) {
            binding.textPrimaryContactName.setText(primaryContact.getName());
            binding.textPrimaryContactPhone.setText(primaryContact.getPhoneNumber());
            binding.textNoPrimaryContact.setVisibility(View.GONE);
            binding.textPrimaryContactName.setVisibility(View.VISIBLE);
            binding.textPrimaryContactPhone.setVisibility(View.VISIBLE);
        } else {
            binding.textNoPrimaryContact.setVisibility(View.VISIBLE);
            binding.textPrimaryContactName.setVisibility(View.GONE);
            binding.textPrimaryContactPhone.setVisibility(View.GONE);
        }

        // Load and display the list of priority contacts
        priorityContactList = contactsManager.getPriorityContacts();
        contactsAdapter.updateContacts(priorityContactList);
    }

    /**
     * Shows a dialog for adding a new contact or editing an existing one.
     *
     * @param existingContact The contact to edit, or null to add a new one.
     * @param isPrimary       True if this dialog is for the primary contact.
     */
    private void showAddEditContactDialog(@Nullable final Contact existingContact, final boolean isPrimary) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_contact, null);

        final EditText nameEditText = dialogView.findViewById(R.id.edit_text_contact_name);
        final EditText phoneEditText = dialogView.findViewById(R.id.edit_text_contact_phone);

        String title = (existingContact == null) ? "Add Contact" : "Edit Contact";
        if (isPrimary) {
            title = (existingContact == null) ? "Set Primary Contact" : "Edit Primary Contact";
        }
        builder.setView(dialogView).setTitle(title);

        if (existingContact != null) {
            nameEditText.setText(existingContact.getName());
            phoneEditText.setText(existingContact.getPhoneNumber());
        }

        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                String name = nameEditText.getText().toString().trim();
                String phone = phoneEditText.getText().toString().trim();

                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(phone)) {
                    Toast.makeText(getContext(), "Name and phone number cannot be empty.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Contact newContact = new Contact(name, phone);

                if (isPrimary) {
                    contactsManager.savePrimaryContact(newContact);
                } else {
                    if (existingContact != null) {
                        // For editing, we remove the old one and add the updated one.
                        contactsManager.deletePriorityContact(existingContact);
                    }
                    contactsManager.addPriorityContact(newContact);
                }
                loadContacts(); // Refresh the UI
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });

        builder.create().show();
    }

    /**
     * This method is called from the ContactsAdapter when the user clicks the options button.
     *
     * @param contact The contact for which the options were clicked.
     */
    @Override
    public void onContactOptionsClicked(final Contact contact) {
        View anchorView = binding.recyclerViewContacts.findViewHolderForAdapterPosition(priorityContactList.indexOf(contact)).itemView.findViewById(R.id.button_contact_options);
        PopupMenu popup = new PopupMenu(requireContext(), anchorView);
        popup.getMenuInflater().inflate(R.menu.contact_options_menu, popup.getMenu());

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.action_edit_contact) {
                    showAddEditContactDialog(contact, false);
                    return true;
                } else if (itemId == R.id.action_delete_contact) {
                    contactsManager.deletePriorityContact(contact);
                    loadContacts();
                    Toast.makeText(getContext(), "Contact deleted.", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            }
        });

        popup.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
          }
