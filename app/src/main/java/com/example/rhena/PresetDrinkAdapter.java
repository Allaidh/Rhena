package com.example.rhena;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.concurrent.Executors;

public class PresetDrinkAdapter extends RecyclerView.Adapter<PresetDrinkAdapter.ViewHolder> {

    private final List<PresetDrink> drinks;

    public PresetDrinkAdapter(List<PresetDrink> drinks) {
        this.drinks = drinks;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_preset_drink, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PresetDrink drink = drinks.get(position);
        holder.tvName.setText(drink.getName());
        holder.tvVolume.setText(drink.getVolume() + " ml");
        holder.tvCaffeine.setText(drink.getCaffeine() + " mg");

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.putExtra("name", drink.getName());
            intent.putExtra("volume", drink.getVolume());
            intent.putExtra("caffeine", drink.getCaffeine());

            Activity activity = (Activity) v.getContext();
            activity.setResult(Activity.RESULT_OK, intent);
            activity.finish();
        });

        holder.itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(v.getContext())
                    .setTitle("Delete Preset")
                    .setMessage("Are you sure you want to delete this preset?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            AppDatabase.getDatabase(v.getContext().getApplicationContext()).drinkDao().deletePresetDrink(drink);
                        });
                        int currentPosition = holder.getAdapterPosition();
                        if (currentPosition != RecyclerView.NO_POSITION) {
                            drinks.remove(currentPosition);
                            notifyItemRemoved(currentPosition);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return drinks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvVolume, tvCaffeine;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPresetName);
            tvVolume = itemView.findViewById(R.id.tvPresetVolume);
            tvCaffeine = itemView.findViewById(R.id.tvPresetCaffeine);
        }
    }
}
