// app/audience/page.tsx

"use client";

import { useEffect, useState } from "react";

import { motion } from "framer-motion";

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
  "TEAM SWAP",
  "TRIPLE POINTS",
  "STEAL POINTS",
  "MYSTERY",
  "FREE 1000",
];

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

    showChaosWheel: false,

    chaosResult: "",
  });

  const [showIntro, setShowIntro] = useState(true);

  const [rotation, setRotation] = useState(0);

  const [spinning, setSpinning] = useState(false);

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

  const spinWheel = async () => {

    if (spinning) return;

    setSpinning(true);

    const randomIndex = Math.floor(
      Math.random() * wheelOptions.length
    );

    const chosen = wheelOptions[randomIndex];

    const degreesPerOption = 360 / wheelOptions.length;

    const stopRotation =
      3600 +
      (360 - randomIndex * degreesPerOption);

    setRotation(stopRotation);

    setTimeout(async () => {

      await update(ref(db, "game"), {
        chaosResult: chosen,
      });

      setSpinning(false);

    }, 5000);

  };

  if (showIntro) {

    return (

      <div className="min-h-screen bg-black flex items-center justify-center relative overflow-hidden">

        <video
          autoPlay
          muted
          loop
          playsInline
          preload="auto"
          className="absolute inset-0 w-full h-full object-cover opacity-30"
        >
          <source src="/videos/background.mp4" type="video/mp4" />
        </video>

        <div className="absolute inset-0 bg-black/60" />

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

    <div className="min-h-screen bg-black text-white relative overflow-hidden">

      <video
        autoPlay
        muted
        loop
        playsInline
        preload="auto"
        className="fixed inset-0 w-full h-full object-cover opacity-30 z-0"
      >
        <source src="/videos/background.mp4" type="video/mp4" />
      </video>

      <div className="fixed inset-0 bg-black/50 z-0" />

      <div className="relative z-10 p-10">

        <h1 className="text-center text-yellow-400 text-9xl font-black mb-10">
          CRACK IT!
        </h1>

        <div className="bg-yellow-400 text-black text-center text-5xl font-black p-10 rounded-3xl">
          {gameState.question}
        </div>

        <div className="flex justify-center gap-10 flex-wrap mt-14">

          {[
            {
              name: gameState.teamAName,
              score: gameState.teamA,
              avatar: gameState.teamAAvatar,
              color: "bg-blue-600",
            },
            {
              name: gameState.teamBName,
              score: gameState.teamB,
              avatar: gameState.teamBAvatar,
              color: "bg-pink-600",
            },
            {
              name: gameState.teamCName,
              score: gameState.teamC,
              avatar: gameState.teamCAvatar,
              color: "bg-green-600",
            },
          ].map((team) => (

            <div
              key={team.name}
              className={`w-[320px] h-[430px] rounded-3xl p-8 ${team.color} flex flex-col items-center justify-between`}
            >

              <div className="text-8xl">
                {team.avatar}
              </div>

              <div className="text-5xl font-black text-center">
                {team.name}
              </div>

              <div className="flex items-center justify-center w-full h-[180px] overflow-hidden">
                <motion.div
                  key={team.score}
                  initial={{ scale: 1.5 }}
                  animate={{ scale: 1 }}
                  className="text-[120px] leading-none font-black text-center"
                >
                  {team.score}
                </motion.div>
              </div>

            </div>

          ))}

        </div>

      </div>

      {/* CHAOS POPUP */}

      {gameState.showChaosWheel && (

        <div className="fixed inset-0 bg-black/90 z-50 flex flex-col items-center justify-center">

          <h1 className="text-yellow-400 text-8xl font-black mb-10">
            CHAOS ROUND
          </h1>

          <div className="w-0 h-0 border-l-[40px] border-r-[40px] border-b-[80px] border-l-transparent border-r-transparent border-b-yellow-400 z-20 mb-[-20px]" />

          <motion.div
            animate={{
              rotate: rotation,
            }}
            transition={{
              duration: 5,
              ease: "easeOut",
            }}
            className="relative w-[700px] h-[700px] rounded-full overflow-hidden border-[16px] border-blue-900"
          >

            {wheelOptions.map((option, index) => {

              const colors = [
                "#ec4899",
                "#f97316",
                "#ef4444",
                "#d946ef",
                "#22c55e",
                "#eab308",
                "#0ea5e9",
                "#9333ea",
              ];

              return (

                <div
                  key={option}
                  className="absolute w-1/2 h-1/2 origin-bottom-right"
                  style={{
                    transform: `rotate(${index * 45}deg) skewY(-45deg)`,
                    background: colors[index],
                    right: "50%",
                    bottom: "50%",
                  }}
                >

                  <div
                    className="absolute text-white font-black text-center text-2xl"
                    style={{
                      transform: "skewY(45deg) rotate(22.5deg)",
                      width: "240px",
                      top: "70px",
                      left: "10px",
                    }}
                  >
                    {option}
                  </div>

                </div>

              );

            })}

            <div className="absolute inset-0 flex items-center justify-center">

              <button
                onClick={spinWheel}
                className="w-[180px] h-[180px] rounded-full bg-blue-950 border-8 border-blue-700 text-5xl font-black"
              >
                SPIN
              </button>

            </div>

          </motion.div>

          {gameState.chaosResult && (

            <motion.div
              initial={{
                scale: 0,
                opacity: 0,
              }}
              animate={{
                scale: 1,
                opacity: 1,
              }}
              className="mt-10 bg-black border-4 border-yellow-400 px-14 py-8 rounded-3xl"
            >

              <div className="text-white text-3xl font-black text-center">
                RESULT
              </div>

              <div className="text-yellow-400 text-6xl font-black text-center mt-4">
                {gameState.chaosResult}
              </div>

            </motion.div>

          )}

        </div>

      )}

    </div>

  );

}