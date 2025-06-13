import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  imgPath: string;
  description: ReactNode;
  iconBackground: string;
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Lightweight & Efficient',
    imgPath: 'https://zmp-community.zdn.vn/community/28eb36400905e05bb914.jpg',
    description: (
      <>
        CabinJ is designed to be lightweight with minimal overhead,
        making your Java applications faster and more resource-efficient.
      </>
    ),
    iconBackground: 'linear-gradient(135deg, #06b6d4 0%, #0ea5e9 100%)'
  },
  {
    title: 'Simple & Intuitive',
    imgPath: 'https://zmp-community.zdn.vn/community/82e99f42a00749591016.jpg',
    description: (
      <>
        Get started quickly with CabinJ's intuitive API. Clear conventions
        and straightforward patterns make development a breeze.
      </>
    ),
    iconBackground: 'linear-gradient(135deg, #10b981 0%, #059669 100%)'
  },
  {
    title: 'Highly Extensible',
    imgPath:'https://zmp-community.zdn.vn/community/81929d39a27c4b22126d.jpg',
    description: (
      <>
        Extend and customize CabinJ to fit your needs. The modular architecture
        allows you to use only what you need while adding your own components.
      </>
    ),
    iconBackground: 'linear-gradient(135deg, #8b5cf6 0%, #6d28d9 100%)'
  },
];

function Feature({title, imgPath, description, iconBackground}: FeatureItem) {
  return (
    <div className={clsx('col col--4', styles.featureColumn)}>
      <div className={styles.featureCard}>
        <div className={styles.featureIconContainer} style={{background: iconBackground}}>
          <img src={imgPath} role="img" />
        </div>
        <div className={styles.featureContent}>
          <Heading as="h3" className={styles.featureTitle}>{title}</Heading>
          <p className={styles.featureDescription}>{description}</p>
        </div>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <Heading as="h2" className={styles.featuresTitle}>Why Choose CabinJ?</Heading>
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
