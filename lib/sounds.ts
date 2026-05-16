let currentAudio: HTMLAudioElement | null = null;

export const playSound = (sound: string) => {

  if (currentAudio) {
    currentAudio.pause();
    currentAudio.currentTime = 0;
  }

  currentAudio = new Audio(`/sounds/${sound}`);

  currentAudio.volume = 0.7;

  currentAudio.play();

};

export const stopSound = () => {

  if (currentAudio) {
    currentAudio.pause();
    currentAudio.currentTime = 0;
  }

};