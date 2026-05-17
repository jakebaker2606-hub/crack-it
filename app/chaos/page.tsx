"use client";

import { useState } from "react";
import { motion } from "framer-motion";

const wheelOptions = [
  "DOUBLE POINTS",
  "LOSE 500",
  "BONUS ROUND",
  "TEAM SWAP",
  "TRIPLE POINTS",
  "STEAL POINTS",
  "MYSTERY CHALLENGE",
  "FREE 1000",
];

export default function ChaosPage() {

  const [spinning, setSpinning] = useState(false);

  const [selected, setSelected] = useState("");

  const [rotation, setRotation] = useState(0);

  const spinWheel = () => {

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

    setTimeout(() => {

      setSelected(chosen);

      setSpinning(false);

    }, 5000);

  };

  return (

    <div className="min-h-screen overflow-hidden bg-gradient-to-br from-purple-700 via-indigo-900 to-black text-white relative">

      {/* BACKGROUND GLOW */}

      <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(255,255,255,0.08),transparent_60%)]" />

      {/* HEADER */}

      <div className="relative z-10 text-center pt-8">

        <motion.div
          animate={{
            scale: [1, 1.08, 1],
          }}
          transition={{
            repeat: Infinity,
            duration: 2,
          }}
          className="inline-block bg-purple-600 px-10 py-4 rounded-3xl text-5xl font-black shadow-2xl"
        >
          CHAOS ROUND
        </motion.div>

        <motion.h1
          animate={{
            scale: [1, 1.03, 1],
          }}
          transition={{
            repeat: Infinity,
            duration: 2,
          }}
          className="text-yellow-400 text-8xl md:text-9xl font-black mt-8 drop-shadow-[0_0_40px_gold]"
        >
          CRACK IT!
        </motion.h1>

      </div>

      {/* WHEEL */}

      <div className="relative z-10 flex flex-col items-center justify-center mt-10">

        {/* POINTER */}

        <div className="w-0 h-0 border-l-[40px] border-r-[40px] border-b-[80px] border-l-transparent border-r-transparent border-b-yellow-400 z-20 mb-[-20px]" />

        {/* WHEEL CONTAINER */}

        <motion.div
          animate={{
            rotate: rotation,
          }}
          transition={{
            duration: 5,
            ease: "easeOut",
          }}
          className="relative w-[700px] h-[700px] rounded-full border-[14px] border-blue-900 shadow-[0_0_80px_#60a5fa] overflow-hidden"
        >

          {/* OUTER LIGHTS */}

          <div className="absolute inset-0 rounded-full border-[16px] border-blue-950" />

          {/* SEGMENTS */}

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

            const rotate = index * 45;

            return (

              <div
                key={option}
                className="absolute w-1/2 h-1/2 origin-bottom-right"
                style={{
                  transform: `rotate(${rotate}deg) skewY(-45deg)`,
                  background: colors[index],
                  right: "50%",
                  bottom: "50%",
                }}
              >

                <div
                  className="absolute flex items-center justify-center text-center text-white font-black text-2xl px-6"
                  style={{
                    transform: "skewY(45deg) rotate(22.5deg)",
                    width: "250px",
                    height: "250px",
                    top: "40px",
                    left: "20px",
                  }}
                >
                  {option}
                </div>

              </div>

            );

          })}

          {/* CENTER */}

          <div className="absolute inset-0 flex items-center justify-center">

            <button
              onClick={spinWheel}
              className="w-[180px] h-[180px] rounded-full bg-blue-950 border-8 border-blue-700 text-5xl font-black shadow-[0_0_40px_#3b82f6] hover:scale-105 transition"
            >
              SPIN
            </button>

          </div>

        </motion.div>

        {/* RESULT */}

        {selected && (

          <motion.div
            initial={{
              scale: 0,
              opacity: 0,
            }}
            animate={{
              scale: 1,
              opacity: 1,
            }}
            className="mt-16 bg-black/70 border-4 border-yellow-400 px-16 py-10 rounded-3xl shadow-[0_0_50px_gold]"
          >

            <div className="text-3xl font-black text-white text-center">
              RESULT
            </div>

            <div className="text-yellow-400 text-6xl font-black text-center mt-4">
              {selected}
            </div>

          </motion.div>

        )}

      </div>

    </div>

  );

}