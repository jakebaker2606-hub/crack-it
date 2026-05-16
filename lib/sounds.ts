export const playSound = (sound: string) => {

  const audio = new Audio(`/sounds/${sound}`);

  audio.volume = 0.7;

  audio.play();

};