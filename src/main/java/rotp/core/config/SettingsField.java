package rotp.core.config;

public interface SettingsField<T> {
	T get();
	void set(T value);
}
