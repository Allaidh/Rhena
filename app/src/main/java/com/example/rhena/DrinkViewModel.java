package com.example.rhena;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class DrinkViewModel extends ViewModel {
    private final MutableLiveData<Integer> selectedDrinkId = new MutableLiveData<>(-1);

    public void selectDrink(int drinkId) {
        selectedDrinkId.setValue(drinkId);
    }

    public LiveData<Integer> getSelectedDrinkId() {
        return selectedDrinkId;
    }
    
    public void clearSelection() {
        selectedDrinkId.setValue(-1);
    }
}