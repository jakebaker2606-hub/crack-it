"use client";

import { useEffect, useRef, useState } from "react";

import { db } from "../../lib/firebase";

import {
  ref,
  onValue,
  update,
} from "firebase/database";

const wheelOptions = [
  "DOUBLE POINTS",
  "LOSE 500",
  "BONUS ROUND",
  "STEAL POINTS",
  "FREE 1000",
  "MYSTERY",
  "TRIPLE POINTS",
  "SWAP SCORES",
];

export default function AudiencePage() {

  const [game, setGame] = useState<any>({});

  const [rotation, setRotation] = useState(0);

  const [spinning, setSpinning] = useState(false);

  const timerAudio = useRef<HTMLAudioElement | null>(null);

  const winnerAudio = useRef<HTMLAudioElement | null>(null);

  const introAudio = useRef<HTMLAudioElement | null>(null);

  const chaosAudio = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {

    timerAudio.current = new Audio("/sounds/timer.mp3");

    winnerAudio.current = new Audio("/sounds/winner.mp3");

    introAudio.current = new Audio("/sounds/intro.mp3");

    chaosAudio.current = new Audio("/sounds/chaos.mp3");

    timerAudio.current.loop = true;

  }, []);

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, async (snapshot) => {

      const data = snapshot.val();

      if (!data) return;

      setGame(data);

      /* TIMER AUDIO */

      if (data.timerRunning) {

        try {
          await timerAudio.current?.play();
        } catch {}

      } else {

        timerAudio.current?.pause();

        if (timerAudio.current) {
          timerAudio.current.currentTime = 0;
        }

      }

      /* INTRO AUDIO */

      if (data.showIntro) {

        try {
          await introAudio.current?.play();
        } catch {}

      }

      /* WINNER AUDIO */

      if (data.winner) {

        try {
          await winnerAudio.current?.play();
        } catch {}

      }

      /* CHAOS */

      if (
        data.showChaosWheel &&
        !data.chaosResult &&
        !spinning
      ) {

        try {
          await chaosAudio.current?.play();
        } catch {}

        spinWheel();

      }

    });

    return () => unsubscribe();

  }, [spinning]);

  useEffect(() => {

    let interval: any;

    if (
      game.timerRunning &&
      game.timer > 0
    ) {

      interval = setInterval(async () => {

        await update(ref(db, "game"), {
          timer: game.timer - 1,
        });

      }, 1000);

    }

    return () => clearInterval(interval);

  }, [
    game.timerRunning,
    game.timer,
  ]);

  const spinWheel = async () => {

    setSpinning(true);

    const randomIndex = Math.floor(
      Math.random() * wheelOptions.length
    );

    const selected = wheelOptions[randomIndex];

    const degreesPerOption =
      360 / wheelOptions.length;

    const finalRotation =
      3600 +
      (360 - randomIndex * degreesPerOption);

    setRotation(finalRotation);

    setTimeout(async () => {

      await update(ref(db, "game"), {
        chaosResult: selected,
      });

      setSpinning(false);

    }, 5000);

  };

  const formatScore = (score: number) => {

    if (!score) return 0;

    if (score >= 1000000) {
      return `${(score / 1000000).toFixed(1)}M`;
    }

    if (score >= 1000) {
      return `${(score / 1000).toFixed(1)}K`;
    }

    return score;

  };

  const leaderboard = [
    {
      name: "TEAM A",
      score: game.teamA || 0,
      color: "bg-blue-600",
    },
    {
      name: "TEAM B",
      score: game.teamB || 0,
      color: "bg-pink-600",
    },
    {
      name: "TEAM C",
      score: game.teamC || 0,
      color: "bg-green-600",
    },
  ].sort((a, b) => b.score - a.score);

  return (

    <div className="min-h-screen bg-black text-white relative overflow-hidden">

      {/* VIDEO BACKGROUND */}

      <video
        autoPlay
        muted
        loop
        playsInline
        className="fixed inset-0 w-full h-full object-cover opacity-30"
      >
        <source
          src="/videos/background.mp4"
          type="video/mp4"
        />
      </video>

      <div className="fixed inset-0 bg-black/60" />

      {/* MAIN */}

      <div className="relative z-10 p-10">

        <h1 className="text-yellow-400 text-8xl font-black text-center tracking-widest">
          CRACK IT!
        </h1>

        {/* TIMER */}

        {game.showTimer && (

          <div className="flex justify-center mt-8">

            <div className="bg-red-600 text-white text-7xl font-black px-20 py-10 rounded-3xl animate-pulse shadow-2xl">
              {game.timer}
            </div>

          </div>

        )}

        {/* QUESTION */}

        <div className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl mt-10 shadow-2xl">
          {game.question || "Waiting for next question..."}
        </div>

        {/* ANSWER */}

        {game.showAnswer && (

          <div className="bg-green-500 text-white text-center text-5xl font-black p-10 rounded-3xl mt-6 shadow-2xl">
            ANSWER: {game.answer}
          </div>

        )}

        {/* TEAMS */}

        <div className="flex justify-center gap-10 mt-16 flex-wrap">

          {[
            {
              name: "TEAM A",
              score: game.teamA,
              color: "bg-blue-600",
            },
            {
              name: "TEAM B",
              score: game.teamB,
              color: "bg-pink-600",
            },
            {
              name: "TEAM C",
              score: game.teamC,
              color: "bg-green-600",
            },
          ].map((team) => (

            <div
              key={team.name}
              className={`${team.color} w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center shadow-2xl border-4 border-white`}
            >

              <div className="text-5xl font-black">
                {team.name}
              </div>

              <div className="text-8xl font-black mt-10">
                {formatScore(team.score)}
              </div>

            </div>

          ))}

        </div>

      </div>

    </div>

  );

}