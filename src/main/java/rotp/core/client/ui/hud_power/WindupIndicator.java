package rotp.core.client.ui.hud_power;

public class WindupIndicator {
	public float value;
	public float maxValue;
	
	public void copyFrom(WindupIndicator src) {
		this.value = src.value;
		this.maxValue = src.maxValue;
	}
}
