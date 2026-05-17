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

    if (game.timerRunning && game.timer > 0) {

      interval = setInterval(async () => {

        await update(ref(db, "game"), {
          timer: game.timer - 1,
        });

      }, 1000);

    }

    return () => clearInterval(interval);

  }, [game.timerRunning, game.timer]);

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

    <div className="min-h-screen bg-black text-white relative overflow-hidden">

      <video
        autoPlay
        muted
        loop
        playsInline
        className="fixed inset-0 w-full h-full object-cover opacity-30"
      >
        <source src="/videos/background.mp4" type="video/mp4" />
      </video>

      <div className="fixed inset-0 bg-black/60" />

      <div className="relative z-10 p-10">

        <h1 className="text-yellow-400 text-8xl font-black text-center">
          CRACK IT!
        </h1>

        {game.showTimer && (

          <div className="flex justify-center mt-8">

            <motion.div
              animate={{
                scale: game.timerRunning
                  ? [1, 1.08, 1]
                  : 1,
              }}
              transition={{
                repeat: Infinity,
                duration: 1,
              }}
              className="bg-red-600 px-16 py-8 rounded-3xl text-7xl font-black"
            >
              {game.timer}
            </motion.div>

          </div>

        )}

        <div className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl mt-10">
          {game.question || "Waiting for next question..."}
        </div>

        {game.showAnswer && (

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
            ANSWER: {game.answer}
          </motion.div>

        )}

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
              className={`${team.color} w-[300px] h-[400px] rounded-3xl flex flex-col items-center justify-center shadow-2xl`}
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