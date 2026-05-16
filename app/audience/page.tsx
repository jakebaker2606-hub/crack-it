"use client";

import { useEffect, useState } from "react";
import { motion } from "framer-motion";

import { db } from "../../lib/firebase";

import {
  ref,
  onValue,
} from "firebase/database";

export default function AudiencePage() {

  const [gameState, setGameState] = useState<any>({
    teamA: 0,
    teamB: 0,
    teamC: 0,

    teamAName: "Team A",
    teamBName: "Team B",
    teamCName: "Team C",

    teamAAvatar: "🦊",
    teamBAvatar: "🐼",
    teamCAvatar: "🐸",

    question: "Waiting for next question...",

    round: 1,

    winner: "",
  });

  const [showIntro, setShowIntro] = useState(true);

  useEffect(() => {

    const timer = setTimeout(() => {
      setShowIntro(false);
    }, 10000);

    return () => clearTimeout(timer);

  }, []);

  useEffect(() => {

    const gameRef = ref(db, "game");

    onValue(gameRef, (snapshot) => {

      const data = snapshot.val();

      if (data) {
        setGameState(data);
      }

    });

  }, []);

  if (showIntro) {

    return (

      <div className="min-h-screen bg-black flex items-center justify-center overflow-hidden relative">

        <video
          autoPlay
          muted
          loop
          playsInline
          className="absolute inset-0 w-full h-full object-cover opacity-40"
        >
          <source src="/videos/background.mp4" type="video/mp4" />
        </video>

        <div className="absolute inset-0 bg-black/50" />

        <motion.h1
          initial={{ scale: 0 }}
          animate={{ scale: 1 }}
          transition={{ duration: 1 }}
          className="text-yellow-400 text-9xl font-black z-10"
        >
          CRACK IT!
        </motion.h1>

      </div>

    );

  }

  return (

    <div className="min-h-screen bg-black text-white overflow-hidden relative">

      {/* BACKGROUND VIDEO */}

      <video
        autoPlay
        muted
        loop
        playsInline
        className="fixed inset-0 w-full h-full object-cover opacity-30 z-0"
      >
        <source src="/videos/background.mp4" type="video/mp4" />
      </video>

      <div className="fixed inset-0 bg-black/40 z-0" />

      {/* MAIN CONTENT */}

      <div className="relative z-10 p-10">

        {/* ROUND */}

        <div className="flex justify-center">

          <motion.div
            animate={{
              scale: [1, 1.08, 1],
            }}
            transition={{
              repeat: Infinity,
              duration: 2,
            }}
            className="bg-purple-600 px-10 py-4 rounded-3xl text-5xl font-black"
          >
            ROUND {gameState.round}
          </motion.div>

        </div>

        {/* TITLE */}

        <motion.h1
          animate={{
            scale: [1, 1.03, 1],
          }}
          transition={{
            repeat: Infinity,
            duration: 2,
          }}
          className="text-center text-yellow-400 text-9xl font-black mt-10 drop-shadow-[0_0_25px_gold]"
        >
          CRACK IT!
        </motion.h1>

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
          className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl mt-10 shadow-2xl"
        >
          {gameState.question}
        </motion.div>

        {/* TEAM PANELS */}

        <div className="flex justify-center gap-10 flex-wrap mt-14">

          {/* TEAM A */}

          <motion.div
            animate={{
              y: [0, -10, 0],
            }}
            transition={{
              repeat: Infinity,
              duration: 3,
            }}
            className="w-[300px] h-[420px] bg-blue-600 rounded-3xl p-10 shadow-[0_0_40px_#3b82f6]"
          >

            <div className="text-8xl text-center">
              {gameState.teamAAvatar}
            </div>

            <div className="text-5xl font-black text-center mt-6">
              {gameState.teamAName}
            </div>

            <motion.div
              key={gameState.teamA}
              initial={{ scale: 1.5 }}
              animate={{ scale: 1 }}
              className="text-center text-9xl font-black mt-10"
            >
              {gameState.teamA}
            </motion.div>

          </motion.div>

          {/* TEAM B */}

          <motion.div
            animate={{
              y: [0, -10, 0],
            }}
            transition={{
              repeat: Infinity,
              duration: 3,
              delay: 0.3,
            }}
            className="w-[300px] h-[420px] bg-pink-600 rounded-3xl p-10 shadow-[0_0_40px_#ec4899]"
          >

            <div className="text-8xl text-center">
              {gameState.teamBAvatar}
            </div>

            <div className="text-5xl font-black text-center mt-6">
              {gameState.teamBName}
            </div>

            <motion.div
              key={gameState.teamB}
              initial={{ scale: 1.5 }}
              animate={{ scale: 1 }}
              className="text-center text-9xl font-black mt-10"
            >
              {gameState.teamB}
            </motion.div>

          </motion.div>

          {/* TEAM C */}

          <motion.div
            animate={{
              y: [0, -10, 0],
            }}
            transition={{
              repeat: Infinity,
              duration: 3,
              delay: 0.6,
            }}
            className="w-[300px] h-[420px] bg-green-600 rounded-3xl p-10 shadow-[0_0_40px_#22c55e]"
          >

            <div className="text-8xl text-center">
              {gameState.teamCAvatar}
            </div>

            <div className="text-5xl font-black text-center mt-6">
              {gameState.teamCName}
            </div>

            <motion.div
              key={gameState.teamC}
              initial={{ scale: 1.5 }}
              animate={{ scale: 1 }}
              className="text-center text-9xl font-black mt-10"
            >
              {gameState.teamC}
            </motion.div>

          </motion.div>

        </div>

        {/* WINNER */}

        {gameState.winner && (

          <motion.div
            initial={{
              scale: 0,
              rotate: -10,
            }}
            animate={{
              scale: 1,
              rotate: 0,
            }}
            className="fixed inset-0 flex items-center justify-center bg-black/80 z-50"
          >

            <motion.div
              animate={{
                scale: [1, 1.08, 1],
              }}
              transition={{
                repeat: Infinity,
                duration: 1,
              }}
              className="text-center"
            >

              <div className="text-yellow-400 text-[140px] font-black">
                {gameState.winner}
              </div>

              <div className="text-white text-7xl font-black mt-4">
                WINS!
              </div>

            </motion.div>

          </motion.div>

        )}

      </div>

    </div>

  );

}