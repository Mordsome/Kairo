package dev.mordo.kairo.render;

/** Added to living entity render states: how white the entity flashes this frame (Kairo Void's hit flash). */
public interface FlashState {
	float kairo$flash();

	void kairo$setFlash(float flash);
}
