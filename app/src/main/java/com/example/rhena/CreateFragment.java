package com.example.rhena;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executors;

public class CreateFragment extends Fragment {

    private EditText etTitle, etDescription, etVolume, etCaffeine, etDate;
    private Button btnSubmit;
    private LinearLayout dateSection;
    private int drinkId = -1;
    private Drink existingDrink;
    private Calendar calendar = Calendar.getInstance();
    private static volatile AppDatabase INSTANCE;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etTitle = view.findViewById(R.id.etTitle);
        etDescription = view.findViewById(R.id.etDescription);
        etVolume = view.findViewById(R.id.etVolume);
        etCaffeine = view.findViewById(R.id.etCaffeine);
        btnSubmit = view.findViewById(R.id.btnSubmit);
        etDate = view.findViewById(R.id.etDate);
        dateSection = view.findViewById(R.id.dateSection);

        DrinkViewModel viewModel = new ViewModelProvider(requireActivity()).get(DrinkViewModel.class);
        viewModel.getSelectedDrinkId().observe(getViewLifecycleOwner(), id -> {
            if (id != null && id != -1) {
                if (drinkId != id) {
                    drinkId = id;
                    dateSection.setVisibility(View.VISIBLE);
                    loadExistingDrink();
                }
            } else {
                if (drinkId != -1) {
                    resetForm();
                }
            }
        });

        etDate.setOnClickListener(v -> showDateTimePicker());
        btnSubmit.setOnClickListener(v -> saveDrink());
        view.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String volStr = etVolume.getText().toString().trim();
            String cafStr = etCaffeine.getText().toString().trim();

            int vol = volStr.isEmpty() ? 0 : Integer.parseInt(volStr);
            int caf = cafStr.isEmpty() ? 0 : Integer.parseInt(cafStr);

            Executors.newSingleThreadExecutor().execute(() -> {
                AppDatabase.getDatabase(requireContext()).drinkDao().insertPresetDrink(
                        new PresetDrink(title, vol, caf)
                );
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Saved to Library!", Toast.LENGTH_SHORT).show()
                );
            });
        });
    }

    private void resetForm() {
        drinkId = -1;
        existingDrink = null;
        etTitle.setText("");
        etDescription.setText("");
        etVolume.setText("");
        etCaffeine.setText("");
        btnSubmit.setText("Submit");
        dateSection.setVisibility(View.GONE);
        calendar = Calendar.getInstance();
    }

    private void loadExistingDrink() {
        Executors.newSingleThreadExecutor().execute(() -> {
            existingDrink = AppDatabase.getDatabase(requireContext().getApplicationContext()).drinkDao().getById(drinkId);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (existingDrink != null) {
                        etTitle.setText(existingDrink.title);
                        etDescription.setText(existingDrink.description);
                        etVolume.setText(String.valueOf(existingDrink.volume));
                        etCaffeine.setText(String.valueOf(existingDrink.caffeine));
                        btnSubmit.setText("Update");

                        calendar.setTimeInMillis(existingDrink.timestamp);
                        updateDateText();
                    }
                });
            }
        });
    }

    private void showDateTimePicker() {
        new DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            new TimePickerDialog(requireContext(), (timeView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                updateDateText();
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
        etDate.setText(sdf.format(calendar.getTime()));
    }

    private void saveDrink() {
        String title = etTitle.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String volumeStr = etVolume.getText().toString().trim();
        String caffeineStr = etCaffeine.getText().toString().trim();

        if (title.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a title", Toast.LENGTH_SHORT).show();
            return;
        }

        int tempVolume = 0;
        try {
            if (!volumeStr.isEmpty()) tempVolume = Integer.parseInt(volumeStr);
        } catch (NumberFormatException ignored) {}

        int tempCaffeine = 0;
        try {
            if (!caffeineStr.isEmpty()) tempCaffeine = Integer.parseInt(caffeineStr);
        } catch (NumberFormatException ignored) {}

        final int volume = tempVolume;
        final int caffeine = tempCaffeine;
        final Drink drinkToSave = existingDrink;
        final long timestamp = calendar.getTimeInMillis();

        Executors.newSingleThreadExecutor().execute(() -> {
            DrinkDao dao = AppDatabase.getDatabase(requireContext().getApplicationContext()).drinkDao();
            if (drinkToSave != null) {
                drinkToSave.title = title;
                drinkToSave.description = description;
                drinkToSave.volume = volume;
                drinkToSave.caffeine = caffeine;
                drinkToSave.timestamp = timestamp;
                dao.update(drinkToSave);
            } else {
                Drink drink = new Drink(title, description, volume, caffeine, timestamp);
                dao.insert(drink);
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), drinkToSave != null ? "Drink updated!" : "Drink saved!", Toast.LENGTH_SHORT).show();
                    DrinkViewModel viewModel = new ViewModelProvider(requireActivity()).get(DrinkViewModel.class);
                    viewModel.clearSelection();
                    resetForm();
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).findViewById(R.id.viewPager).post(() -> {
                            ((androidx.viewpager2.widget.ViewPager2) getActivity().findViewById(R.id.viewPager)).setCurrentItem(0);
                        });
                    }
                });
            }
        });
    }
}