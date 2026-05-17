"use client";

import { useEffect, useRef, useState } from "react";

import { motion } from "framer-motion";

import { db } from "../../lib/firebase";

import {
  ref,
  onValue,
  update,
} from "firebase/database";

export default function AudiencePage() {

  const [gameState, setGameState] = useState<any>({
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

      setGameState(data);

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
      gameState.timerRunning &&
      gameState.timer > 0
    ) {

      interval = setInterval(async () => {

        await update(ref(db, "game"), {
          timer: gameState.timer - 1,
        });

      }, 1000);

    }

    return () => clearInterval(interval);

  }, [
    gameState.timerRunning,
    gameState.timer,
  ]);

  const formatScore = (score: number) => {

    if (score >= 1000000) {
      return `${(score / 1000000).toFixed(1)}M`;
    }

    if (score >= 1000) {
      return `${(score / 1000).toFixed(1)}K`;
    }

    return score.toString();

  };

  return (

    <div className="min-h-screen bg-black text-white relative overflow-hidden">

      {/* BACKGROUND VIDEO */}

      <video
        autoPlay
        muted
        loop
        playsInline
        className="fixed inset-0 w-full h-full object-cover opacity-30 z-0"
      >
        <source
          src="/videos/background.mp4"
          type="video/mp4"
        />
      </video>

      <div className="fixed inset-0 bg-black/60 z-0" />

      {/* MAIN */}

      <div className="relative z-10 p-10">

        <h1 className="text-center text-yellow-400 text-9xl font-black">
          CRACK IT!
        </h1>

        {/* TIMER */}

        {gameState.showTimer && (

          <div className="flex justify-center mt-6">

            <motion.div
              animate={{
                scale: gameState.timerRunning
                  ? [1, 1.08, 1]
                  : 1,
              }}
              transition={{
                repeat: Infinity,
                duration: 1,
              }}
              className="bg-red-600 text-white text-7xl font-black px-14 py-6 rounded-3xl"
            >
              {gameState.timer}
            </motion.div>

          </div>

        )}

        {/* QUESTION */}

        <motion.div
          key={gameState.question}
          initial={{
            opacity: 0,
            scale: 0.8,
          }}
          animate={{
            opacity: 1,
            scale: 1,
          }}
          className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl mt-10"
        >
          {gameState.question || "Waiting for next question..."}
        </motion.div>

        {/* ANSWER */}

        {gameState.showAnswer &&
          gameState.answer && (

          <motion.div
            initial={{
              scale: 0,
              opacity: 0,
            }}
            animate={{
              scale: 1,
              opacity: 1,
            }}
            className="bg-green-500 text-white text-center text-5xl font-black p-10 rounded-3xl mt-6"
          >
            ANSWER: {gameState.answer}
          </motion.div>

        )}

        {/* TEAMS */}

        <div className="flex justify-center gap-10 flex-wrap mt-14">

          {[
            {
              name: "TEAM A",
              score: gameState.teamA,
              color: "bg-blue-600",
            },
            {
              name: "TEAM B",
              score: gameState.teamB,
              color: "bg-pink-600",
            },
            {
              name: "TEAM C",
              score: gameState.teamC,
              color: "bg-green-600",
            },
          ].map((team) => (

            <motion.div
              key={team.name}
              animate={{
                y: [0, -10, 0],
              }}
              transition={{
                repeat: Infinity,
                duration: 3,
              }}
              className={`${team.color} w-[320px] h-[420px] rounded-3xl flex flex-col items-center justify-center shadow-2xl`}
            >

              <div className="text-5xl font-black">
                {team.name}
              </div>

              <div className="text-[90px] font-black mt-10">
                {formatScore(team.score)}
              </div>

            </motion.div>

          ))}

        </div>

      </div>

    </div>

  );

}