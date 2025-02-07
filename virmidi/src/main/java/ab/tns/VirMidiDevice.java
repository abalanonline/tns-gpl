/*
 * Copyright (C) 2025 Aleksei Balan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ab.tns;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VirMidiDevice implements MidiDevice {

  private MidiDevice midiDevice;

  @Override
  public Info getDeviceInfo() {
    return null;
  }

  @Override
  public void open() throws MidiUnavailableException {
  }

  @Override
  public void close() {
    midiDevice.close();
  }

  @Override
  public boolean isOpen() {
    return midiDevice.isOpen();
  }

  @Override
  public long getMicrosecondPosition() {
    return 0;
  }

  @Override
  public int getMaxReceivers() {
    return 0;
  }

  @Override
  public int getMaxTransmitters() {
    return 0;
  }

  @Override
  public Receiver getReceiver() throws MidiUnavailableException {
    return null;
  }

  @Override
  public List<Receiver> getReceivers() {
    return null;
  }

  @Override
  public Transmitter getTransmitter() throws MidiUnavailableException {
    return midiDevice.getTransmitter();
  }

  @Override
  public List<Transmitter> getTransmitters() {
    return null;
  }

  public static void main(String[] args) {
    try (Synthesizer synthesizer = MidiSystem.getSynthesizer(); VirMidiDevice virMidiDevice = new VirMidiDevice()) {
      synthesizer.open();
      virMidiDevice.open();
      virMidiDevice.getTransmitter().setReceiver(synthesizer.getReceiver());
      System.in.read();
    } catch (MidiUnavailableException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
