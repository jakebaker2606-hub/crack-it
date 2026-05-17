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
  "MYSTERY",
  "FREE 1000",
];

export default function ChaosPopup() {

  const [showWheel, setShowWheel] = useState(false);

  const [rotation, setRotation] = useState(0);

  const [selected, setSelected] = useState("");

  const [spinning, setSpinning] = useState(false);

  const spinWheel = () => {

    if (spinning) return;