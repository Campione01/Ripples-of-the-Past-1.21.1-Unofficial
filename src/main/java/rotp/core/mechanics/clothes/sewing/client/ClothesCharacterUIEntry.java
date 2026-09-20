package rotp.core.mechanics.clothes.sewing.client;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import rotp.core.mechanics.clothes.itemdata.ClothesSet;
import rotp.core.mechanics.clothes.itemdata.StoryCharacter;
import rotp.core.subsystems.StoryPart;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

public class ClothesCharacterUIEntry {
	public final SewingMachineScreen screen;
	public final Holder<StoryCharacter> character;
	public final List<Holder<ClothesSet>> allClothesSets;
	public List<Holder<ClothesSet>> filteredClothesSets;
	public Holder<ClothesSet> selectedSet;

	public ClothesCharacterUIEntry(Component message, Holder<StoryCharacter> character, List<Holder<ClothesSet>> clothesSets, 
			SewingMachineScreen screen, @Nullable ClothesCharacterUIEntry prev) {
		this.screen = screen;
		this.allClothesSets = clothesSets;
		this.character = character;
		setFilteredClothesSets(clothesSets);
		if (prev != null && allClothesSets.contains(prev.selectedSet)) {
			this.selectedSet = prev.selectedSet;
		}
		else {
			this.selectedSet = filteredClothesSets.isEmpty() ? null : filteredClothesSets.get(0);
		}
	}

	public Holder<StoryCharacter> getCharacter() {
		return character;
	}

	public Stream<Holder<ClothesSet>> getAllUnlockedSets() {
		return allClothesSets.stream();
	}

	public boolean filter(Set<StoryPart> partFilters, String searchBar, boolean searchByName) {
		setFilteredClothesSets(allClothesSets.stream()
				.filter(set -> 
				set.value().getStoryPart().map(Holder::value).filter(partFilters::contains).isPresent() && 
				matchesSearch(searchBar, searchByName, character.value().getName(false).getString(),
						set.value().getName().getString())).collect(Collectors.toList()));
//		if (!filteredClothesSets.isEmpty() && !filteredClothesSets.contains(selectedSet)) {
//			setSelectedSet(filteredClothesSets.get(0));
//		}
		return !filteredClothesSets.isEmpty();
	}

	static boolean matchesSearch(String search, boolean searchByName, String characterName, String clothesName) {
		String query = search.strip().toLowerCase(Locale.ROOT);
		return query.isEmpty() || searchByName && (characterName.toLowerCase(Locale.ROOT).contains(query)
				|| clothesName.toLowerCase(Locale.ROOT).contains(query));
	}

	public Holder<ClothesSet> getSelectedSet() {
		return selectedSet;
	}

	public void setSelectedSet(Holder<ClothesSet> set) {
		this.selectedSet = set;
		Holder<ClothesSet> currentlyRendered = screen.getSettings().getSelectedSet();
		if (screen.getSettings().getSelectedCharacter() == this.character.value()
				&& (currentlyRendered == null || currentlyRendered.value() != set.value())) {
			screen.getSettings().selectSet(screen, selectedSet);
		}
	}

	protected void setFilteredClothesSets(List<Holder<ClothesSet>> list) {
		this.filteredClothesSets = list;
		if (screen.getSettings().getSelectedCharacter() == this.character.value()) {
			setupClothesSelectionUI();
		}
	}

	public void setupClothesSelectionUI() {
		screen.setClothesSelection(this, filteredClothesSets);
	}
}
