"use client";

import { useEffect, useRef, useState } from "react";

import { db } from "../../lib/firebase";

import {
  ref,
  onValue,
  update,
} from "firebase/database";

export default function AudiencePage() {

  const [game, setGame] = useState<any>({
    teamA: 0,
    teamB: 0,
    teamC: 0,
    question: "",
    answer: "",
    showAnswer: false,
    timer: 30,
    timerRunning: false,
    showTimer: false,
  });

  const timerAudio = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {

    timerAudio.current = new Audio("/sounds/timer.mp3");

    timerAudio.current.loop = true;

  }, []);

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, async (snapshot) => {

      const data = snapshot.val();

      if (!data) return;

      setGame(data);

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

    });

    return () => unsubscribe();

  }, []);

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

  const formatScore = (score: number) => {

    if (score >= 1000000) {
      return `${(score / 1000000).toFixed(1)}M`;
    }

    if (score >= 1000) {
      return `${(score / 1000).toFixed(1)}K`;
    }

    return score;

  };

  return (

    <div className="min-h-screen bg-black text-white p-10">

      <h1 className="text-yellow-400 text-8xl font-black text-center">
        CRACK IT!
      </h1>

      {/* TIMER */}

      {game.showTimer && (

        <div className="flex justify-center mt-8">

          <div className="bg-red-600 text-white text-7xl font-black px-16 py-8 rounded-3xl">
            {game.timer}
          </div>

        </div>

      )}

      {/* QUESTION */}

      <div className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl mt-10">
        {game.question || "Waiting for next question..."}
      </div>

      {/* ANSWER */}

      {game.showAnswer && (

        <div className="bg-green-500 text-white text-center text-5xl font-black p-10 rounded-3xl mt-6">
          ANSWER: {game.answer}
        </div>

      )}

      {/* TEAMS */}

      <div className="flex justify-center gap-10 mt-16 flex-wrap">

        <div className="bg-blue-600 w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center">

          <div className="text-5xl font-black">
            TEAM A
          </div>

          <div className="text-8xl font-black mt-10">
            {formatScore(game.teamA || 0)}
          </div>

        </div>

        <div className="bg-pink-600 w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center">

          <div className="text-5xl font-black">
            TEAM B
          </div>

          <div className="text-8xl font-black mt-10">
            {formatScore(game.teamB || 0)}
          </div>

        </div>

        <div className="bg-green-600 w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center">

          <div className="text-5xl font-black">
            TEAM C
          </div>

          <div className="text-8xl font-black mt-10">
            {formatScore(game.teamC || 0)}
          </div>

        </div>

      </div>

    </div>

  );

}