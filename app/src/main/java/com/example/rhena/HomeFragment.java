package com.example.rhena;

import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class HomeFragment extends Fragment {

    private List<Drink> drinks;
    private ZoneId zone = ZoneId.systemDefault();
    private LinearLayout containerTW;
    private LinearLayout drinkHistoryContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        containerTW = view.findViewById(R.id.weekConsumptionContainer);
        drinkHistoryContainer = view.findViewById(R.id.drinkHistoryContainer);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadDrinks();
    }

    private void loadDrinks() {
        Executors.newSingleThreadExecutor().execute(() -> {
            drinks = AppDatabase.getDatabase(requireContext().getApplicationContext()).drinkDao().getAll();

            LocalDate startOfWeek = LocalDate.now(zone)
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            Instant startInstant = startOfWeek.atStartOfDay(zone).toInstant();
            long startMillis = startInstant.toEpochMilli();

            LocalDate startOfNextWeek = startOfWeek.plusWeeks(1);
            Instant endInstant = startOfNextWeek.atStartOfDay(zone).toInstant();
            long endMillis = endInstant.toEpochMilli();

            List<Drink> drinksThisWeek = drinks.stream()
                    .filter(drink -> drink.timestamp >= startMillis && drink.timestamp < endMillis)
                    .collect(Collectors.toList());

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    updateSummaryUI(drinksThisWeek);
                    updateHistoryUI();
                });
            }
        });
    }

    private void updateSummaryUI(List<Drink> drinksThisWeek) {
        containerTW.removeAllViews();
        
        containerTW.addView(createSummaryItem("Drinks", String.valueOf(drinksThisWeek.size())));
        containerTW.addView(createSummaryItem("Caffeine", drinksThisWeek.stream().mapToInt(Drink::getCaffeine).sum() + "mg"));
        containerTW.addView(createSummaryItem("Volume", drinksThisWeek.stream().mapToInt(Drink::getVolume).sum() + "ml"));
    }

    private View createSummaryItem(String label, String value) {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(VERTICAL);
        layout.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        
        TextView valueTv = new TextView(getContext());
        valueTv.setText(value);
        valueTv.setTextSize(18);
        valueTv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        layout.addView(valueTv);
        
        TextView labelTv = new TextView(getContext());
        labelTv.setText(label);
        labelTv.setTextSize(14);
        labelTv.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        layout.addView(labelTv);
        
        return layout;
    }

    private void updateHistoryUI() {
        drinkHistoryContainer.removeAllViews();
        for (Drink drink : drinks) {
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(VERTICAL);
            layout.setPadding(0, 8, 0, 8);

            LinearLayout layoutTD = new LinearLayout(getContext());
            layoutTD.setOrientation(HORIZONTAL);

            TextView title = new TextView(getContext());
            title.setText(drink.title);
            title.setTextSize(18);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            layoutTD.addView(title);

            TextView dateTv = new TextView(getContext());
            Date drinkDate = new Date(drink.timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
            dateTv.setText(sdf.format(drinkDate));
            layoutTD.addView(dateTv);

            TextView menuButton = new TextView(getContext());
            menuButton.setText("⋮");
            menuButton.setTextSize(20);
            menuButton.setPadding(16, 0, 16, 0);
            menuButton.setOnClickListener(v -> showPopupMenu(v, drink));
            layoutTD.addView(menuButton);

            layout.addView(layoutTD);

            TextView description = new TextView(getContext());
            description.setText(drink.description);
            layout.addView(description);

            layout.addView(createDataRow("Volume (ml) ", String.valueOf(drink.volume)));
            layout.addView(createDataRow("Caffeine (mg) ", String.valueOf(drink.caffeine)));

            layout.setPadding(10, 10, 10, 10);
            drinkHistoryContainer.addView(layout);

            View divider = new View(getContext());
            int dividerHeight = (int) (getResources().getDisplayMetrics().density * 5);
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dividerHeight));
            android.util.TypedValue typedValue = new android.util.TypedValue();
            if (requireContext().getTheme().resolveAttribute(android.R.attr.listDivider, typedValue, true)) {
                divider.setBackgroundResource(typedValue.resourceId);
            }
            drinkHistoryContainer.addView(divider);
        }
    }

    private View createDataRow(String label, String value) {
        TextView tv = new TextView(getContext());
        tv.setText(label + value);
        return tv;
    }

    private void showPopupMenu(View v, Drink drink) {
        PopupMenu popup = new PopupMenu(getContext(), v);
        popup.getMenuInflater().inflate(R.menu.drink_item_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_delete) {
                deleteDrink(drink);
                return true;
            } else if (item.getItemId() == R.id.action_edit) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).navigateToCreate(drink.id);
                }
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void deleteDrink(Drink drink) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getDatabase(requireContext().getApplicationContext()).drinkDao().delete(drink);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Drink deleted", Toast.LENGTH_SHORT).show();
                    loadDrinks();
                });
            }
        });
    }
}