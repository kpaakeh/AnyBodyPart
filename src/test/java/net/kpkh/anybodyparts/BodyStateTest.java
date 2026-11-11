package net.kpkh.anybodyparts;

public final class BodyStateTest {
    public static void main(String[] args) {
        for (int mask = 0; mask <= BodyState.ALL; mask++) {
            BodyState state = new BodyState(mask);
            assert state.headOnly() == (mask == 0);
            assert state.low() == (state.legs() == 0);
            assert state.canJump() == (mask != 0);
            assert state.speed() > 0 && state.speed() <= 1;
            for (BodyPart part : BodyPart.values()) {
                assert state.with(part, true).has(part);
                assert !state.with(part, false).has(part);
                assert state.with(part, !state.has(part)).with(part, state.has(part)).equals(state);
                assert (state.mask() ^ state.with(part, !state.has(part)).mask()) == part.bit();
            }
        }
        assert new BodyState(16).speed() < new BodyState(17).speed();
        assert new BodyState(17).speed() < new BodyState(19).speed();
        assert new BodyState(16).movement().equals("wriggle");
        assert new BodyState(0).movement().equals("roll");
        assert new BodyState(4).movement().equals("hop");
        assert !new BodyState(12).canSprint();
        assert !new BodyState(28).canClimb();
        assert BodyState.COMPLETE.canGlide();
        for (int bad : new int[]{-1, 32, Integer.MAX_VALUE}) {
            try { new BodyState(bad); throw new AssertionError("Invalid mask accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("All 32 body combinations and invalid masks verified.");
    }
}
